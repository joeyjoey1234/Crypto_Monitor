package com.fdroid.cryptomonitor.data.repo

import com.fdroid.cryptomonitor.data.model.AssetAnalysis
import com.fdroid.cryptomonitor.data.model.CryptoAsset
import com.fdroid.cryptomonitor.data.model.PricePoint
import com.fdroid.cryptomonitor.data.model.TradeAction
import com.fdroid.cryptomonitor.data.model.WalletAddresses
import com.fdroid.cryptomonitor.data.remote.BlockchairApi
import com.fdroid.cryptomonitor.data.remote.CoinGeckoApi
import com.fdroid.cryptomonitor.data.remote.MarketCoinDto
import com.fdroid.cryptomonitor.domain.SignalEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import kotlin.math.pow
import kotlin.math.roundToLong

class CryptoRepository(
    private val marketApi: CoinGeckoApi,
    private val walletApi: BlockchairApi,
    private val signalEngine: SignalEngine
) {

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeAssets(
        assets: List<CryptoAsset>,
        walletAddresses: WalletAddresses
    ): List<AssetAnalysis> = coroutineScope {
        if (assets.isEmpty()) return@coroutineScope emptyList()

        val marketData = fetchMarketsWithRateLimitRetry(assets)

        val ethAddress = walletAddresses.forChain("ethereum")
        val baseAddress = walletAddresses.forChain("base")

        val ethNativeBalance = ethAddress?.let { fetchEvmNativeBalance("ethereum", it) }
        val baseNativeBalance = baseAddress?.let { fetchEvmNativeBalance("base", it) }
        val baseTokenBalances = baseAddress?.let { fetchBaseTokenBalances(it) }.orEmpty()

        assets.map { asset ->
            val market = marketData[asset.id]
            val history = market?.let { historyFromSparkline(it) } ?: emptyList()
            val (signals, finalAction) = signalEngine.analyze(history)

            val balance = fetchBalance(
                asset = asset,
                walletAddresses = walletAddresses,
                ethNativeBalance = ethNativeBalance,
                baseNativeBalance = baseNativeBalance,
                baseTokenBalances = baseTokenBalances
            )

            AssetAnalysis(
                asset = asset,
                currentPriceUsd = market?.current_price ?: 0.0,
                balance = balance,
                history = history,
                algorithmSignals = signals,
                finalAction = if (history.isEmpty()) TradeAction.HOLD else finalAction
            )
        }
    }

    private suspend fun fetchMarketsWithRateLimitRetry(assets: List<CryptoAsset>): Map<String, MarketCoinDto> {
        val ids = assets.joinToString(",") { it.id }
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                return marketApi.getMarkets(ids = ids).associateBy { it.id }
            } catch (error: HttpException) {
                if (error.code() == 429 && attempt < 2) {
                    delay((attempt + 1) * 1500L)
                    lastError = error
                    return@repeat
                }
                throw error
            }
        }
        throw lastError ?: IllegalStateException("Unable to fetch market data")
    }

    private fun historyFromSparkline(market: MarketCoinDto): List<PricePoint> {
        val prices = market.sparkline_in_7d?.price?.filter { it > 0.0 }.orEmpty()
        if (prices.size < 2) return emptyList()

        val now = Instant.now()
        val stepSeconds = ((7 * 24 * 3600.0) / (prices.size - 1)).roundToLong().coerceAtLeast(1L)

        return prices.mapIndexed { index, price ->
            val remaining = (prices.lastIndex - index).toLong()
            PricePoint(
                timestamp = now.minusSeconds(stepSeconds * remaining),
                priceUsd = price
            )
        }
    }

    private suspend fun fetchBalance(
        asset: CryptoAsset,
        walletAddresses: WalletAddresses,
        ethNativeBalance: Double?,
        baseNativeBalance: Double?,
        baseTokenBalances: Map<String, Double>
    ): Double? {
        when (asset.chain) {
            "ethereum" -> {
                if (asset.tokenContract != null) {
                    // Token-by-contract lookup for Ethereum can be added if needed.
                    return null
                }
                return ethNativeBalance
            }

            "base" -> {
                if (asset.tokenContract != null) {
                    return baseTokenBalances[asset.tokenContract.lowercase()]
                }
                return baseNativeBalance
            }
        }

        if (!asset.usesNativeBalance) return null
        val walletAddress = walletAddresses.forChain(asset.chain) ?: return null

        val response = runCatching {
            walletApi.getAddress(chain = asset.chain, address = walletAddress)
        }.getOrNull() ?: return null

        val raw = response.data.values.firstOrNull()?.address?.balance ?: return null
        val divisor = 10.0.pow(asset.decimals.toDouble())
        return raw / divisor
    }

    private suspend fun fetchEvmNativeBalance(chain: String, walletAddress: String): Double? = withContext(Dispatchers.IO) {
        val rpcUrl = when (chain) {
            "ethereum" -> "https://cloudflare-eth.com"
            "base" -> "https://mainnet.base.org"
            else -> return@withContext null
        }

        val payload = """{"jsonrpc":"2.0","method":"eth_getBalance","params":["$walletAddress","latest"],"id":1}"""
        val request = Request.Builder()
            .url(rpcUrl)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val hex = root["result"]?.jsonPrimitive?.content ?: return@use null
                hexToAmount(hex, 18)
            }
        }.getOrNull()
    }

    private suspend fun fetchBaseTokenBalances(walletAddress: String): Map<String, Double> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://base.blockscout.com/api/v2/addresses/$walletAddress/token-balances")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyMap()
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonArray

                val result = linkedMapOf<String, Double>()
                root.forEach { entry ->
                    val obj = entry.jsonObject
                    val tokenObj = obj["token"]?.jsonObject ?: return@forEach
                    val contract = tokenObj["address_hash"]?.jsonPrimitive?.content?.lowercase() ?: return@forEach
                    val decimals = tokenObj["decimals"]?.jsonPrimitive?.content?.toIntOrNull() ?: 18
                    val raw = obj["value"]?.jsonPrimitive?.content ?: return@forEach
                    val amount = decimalAmount(raw, decimals)
                    result[contract] = amount
                }
                result
            }
        }.getOrDefault(emptyMap())
    }

    private fun hexToAmount(hex: String, decimals: Int): Double {
        val normalized = hex.removePrefix("0x").ifBlank { "0" }
        val value = BigInteger(normalized, 16)
        return BigDecimal(value).movePointLeft(decimals).toDouble()
    }

    private fun decimalAmount(raw: String, decimals: Int): Double {
        return BigDecimal(raw).movePointLeft(decimals).toDouble()
    }
}
