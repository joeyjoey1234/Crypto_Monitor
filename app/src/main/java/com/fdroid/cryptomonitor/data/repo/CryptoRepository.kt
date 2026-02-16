package com.fdroid.cryptomonitor.data.repo

import com.fdroid.cryptomonitor.data.model.AssetAnalysis
import com.fdroid.cryptomonitor.data.model.CryptoAsset
import com.fdroid.cryptomonitor.data.model.DefaultAssets
import com.fdroid.cryptomonitor.data.model.PricePoint
import com.fdroid.cryptomonitor.data.model.TradeAction
import com.fdroid.cryptomonitor.data.model.WalletAddresses
import com.fdroid.cryptomonitor.data.remote.BlockchairApi
import com.fdroid.cryptomonitor.data.remote.CoinGeckoApi
import com.fdroid.cryptomonitor.data.remote.CoinListItemDto
import com.fdroid.cryptomonitor.data.remote.MarketCoinDto
import com.fdroid.cryptomonitor.domain.SignalEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

    private var baseContractCoinMapCache: Map<String, CoinListItemDto> = emptyMap()
    private var baseContractCoinMapFetchedAtMillis: Long = 0L

    private var baseTokenHoldingsCache: CachedBaseHoldings? = null

    suspend fun resolveTrackedAssets(walletAddresses: WalletAddresses): List<CryptoAsset> {
        val assets = mutableListOf<CryptoAsset>()

        if (walletAddresses.forChain("bitcoin") != null) {
            assets += DefaultAssets.first { it.chain == "bitcoin" }
        }
        if (walletAddresses.forChain("ethereum") != null) {
            assets += DefaultAssets.first { it.chain == "ethereum" }
        }
        if (walletAddresses.forChain("solana") != null) {
            assets += DefaultAssets.first { it.chain == "solana" }
        }
        if (walletAddresses.forChain("dogecoin") != null) {
            assets += DefaultAssets.first { it.chain == "dogecoin" }
        }
        if (walletAddresses.forChain("cardano") != null) {
            assets += DefaultAssets.first { it.chain == "cardano" }
        }

        val baseAddress = walletAddresses.forChain("base")
        if (baseAddress != null) {
            assets += CryptoAsset(
                id = "base-native-eth",
                marketId = "ethereum",
                symbol = "ETH",
                displayName = "Base ETH",
                chain = "base",
                decimals = 18,
                usesNativeBalance = true
            )

            val holdings = fetchBaseTokenHoldings(baseAddress)
                .filter { it.amount > 0.0 }
            if (holdings.isNotEmpty()) {
                val contractMap = fetchBaseContractCoinMap()
                holdings.forEach { holding ->
                    val coin = contractMap[holding.contract.lowercase()] ?: return@forEach
                    assets += CryptoAsset(
                        id = "base:${holding.contract.lowercase()}",
                        marketId = coin.id,
                        symbol = coin.symbol.uppercase(),
                        displayName = coin.name,
                        chain = "base",
                        decimals = holding.decimals,
                        usesNativeBalance = false,
                        tokenContract = holding.contract.lowercase()
                    )
                }
            }
        }

        return assets.distinctBy { it.id }
    }

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
            val market = marketData[asset.marketId]
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
                priceChange24hPct = market?.price_change_percentage_24h,
                balance = balance,
                history = history,
                algorithmSignals = signals,
                finalAction = if (history.isEmpty()) TradeAction.HOLD else finalAction
            )
        }
    }

    private suspend fun fetchMarketsWithRateLimitRetry(assets: List<CryptoAsset>): Map<String, MarketCoinDto> {
        val ids = assets.map { it.marketId }.distinct().joinToString(",")
        if (ids.isBlank()) return emptyMap()

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
                if (asset.tokenContract != null) return null
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

    private suspend fun fetchBaseTokenBalances(walletAddress: String): Map<String, Double> {
        return fetchBaseTokenHoldings(walletAddress)
            .associate { it.contract.lowercase() to it.amount }
    }

    private suspend fun fetchBaseTokenHoldings(walletAddress: String): List<BaseTokenHolding> = withContext(Dispatchers.IO) {
        val cache = baseTokenHoldingsCache
        val now = System.currentTimeMillis()
        if (cache != null && cache.address.equals(walletAddress, ignoreCase = true) && now - cache.fetchedAtMillis < 60_000L) {
            return@withContext cache.holdings
        }

        val request = Request.Builder()
            .url("https://base.blockscout.com/api/v2/addresses/$walletAddress/token-balances")
            .get()
            .build()

        val holdings = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonArray

                root.mapNotNull { entry ->
                    val obj = entry.jsonObject
                    val tokenObj = obj["token"]?.jsonObject ?: return@mapNotNull null
                    val contract = tokenObj["address_hash"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val symbol = tokenObj["symbol"]?.jsonPrimitive?.content.orEmpty()
                    val name = tokenObj["name"]?.jsonPrimitive?.content.orEmpty()
                    val decimals = tokenObj["decimals"]?.jsonPrimitive?.content?.toIntOrNull() ?: 18
                    val raw = obj["value"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val amount = decimalAmount(raw, decimals)
                    BaseTokenHolding(
                        contract = contract.lowercase(),
                        symbol = symbol,
                        name = name,
                        decimals = decimals,
                        amount = amount
                    )
                }
            }
        }.getOrDefault(emptyList())

        baseTokenHoldingsCache = CachedBaseHoldings(
            address = walletAddress,
            fetchedAtMillis = now,
            holdings = holdings
        )
        holdings
    }

    private suspend fun fetchBaseContractCoinMap(): Map<String, CoinListItemDto> {
        val now = System.currentTimeMillis()
        if (baseContractCoinMapCache.isNotEmpty() && now - baseContractCoinMapFetchedAtMillis < 24 * 60 * 60 * 1000L) {
            return baseContractCoinMapCache
        }

        val map = runCatching {
            marketApi.getCoinList(includePlatform = true)
                .mapNotNull { coin ->
                    val contract = coin.platforms?.get("base")?.trim().orEmpty()
                    if (contract.isBlank()) null else contract.lowercase() to coin
                }
                .toMap()
        }.getOrDefault(emptyMap())

        if (map.isNotEmpty()) {
            baseContractCoinMapCache = map
            baseContractCoinMapFetchedAtMillis = now
        }
        return baseContractCoinMapCache
    }

    private fun hexToAmount(hex: String, decimals: Int): Double {
        val normalized = hex.removePrefix("0x").ifBlank { "0" }
        val value = BigInteger(normalized, 16)
        return BigDecimal(value).movePointLeft(decimals).toDouble()
    }

    private fun decimalAmount(raw: String, decimals: Int): Double {
        return BigDecimal(raw).movePointLeft(decimals).toDouble()
    }

    private data class BaseTokenHolding(
        val contract: String,
        val symbol: String,
        val name: String,
        val decimals: Int,
        val amount: Double
    )

    private data class CachedBaseHoldings(
        val address: String,
        val fetchedAtMillis: Long,
        val holdings: List<BaseTokenHolding>
    )
}
