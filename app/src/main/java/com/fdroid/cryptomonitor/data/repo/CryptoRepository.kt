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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.time.Instant
import kotlin.math.pow
import kotlin.math.roundToLong

class CryptoRepository(
    private val marketApi: CoinGeckoApi,
    private val walletApi: BlockchairApi,
    private val signalEngine: SignalEngine
) {

    suspend fun analyzeAssets(
        assets: List<CryptoAsset>,
        walletAddresses: WalletAddresses
    ): List<AssetAnalysis> = coroutineScope {
        val marketData = fetchMarketsWithRateLimitRetry(assets)

        assets.map { asset ->
            async {
                val market = marketData[asset.id]
                val history = market?.let { historyFromSparkline(it) } ?: emptyList()
                val (signals, finalAction) = signalEngine.analyze(history)

                val balance = fetchBalance(asset, walletAddresses)

                AssetAnalysis(
                    asset = asset,
                    currentPriceUsd = market?.current_price ?: 0.0,
                    balance = balance,
                    history = history,
                    algorithmSignals = signals,
                    finalAction = if (history.isEmpty()) TradeAction.HOLD else finalAction
                )
            }
        }.awaitAll()
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

    private suspend fun fetchBalance(asset: CryptoAsset, walletAddresses: WalletAddresses): Double? {
        val walletAddress = walletAddresses.forChain(asset.chain) ?: return null

        val response = runCatching {
            walletApi.getAddress(chain = asset.chain, address = walletAddress)
        }.getOrNull() ?: return null

        val raw = response.data.values.firstOrNull()?.address?.balance ?: return null
        val divisor = 10.0.pow(asset.decimals.toDouble())
        return raw / divisor
    }
}
