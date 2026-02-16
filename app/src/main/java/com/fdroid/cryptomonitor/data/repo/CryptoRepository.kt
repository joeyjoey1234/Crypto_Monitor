package com.fdroid.cryptomonitor.data.repo

import com.fdroid.cryptomonitor.data.model.AssetAnalysis
import com.fdroid.cryptomonitor.data.model.CryptoAsset
import com.fdroid.cryptomonitor.data.model.PricePoint
import com.fdroid.cryptomonitor.data.model.TradeAction
import com.fdroid.cryptomonitor.data.model.WalletAddresses
import com.fdroid.cryptomonitor.data.remote.BlockchairApi
import com.fdroid.cryptomonitor.data.remote.CoinGeckoApi
import com.fdroid.cryptomonitor.domain.SignalEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import kotlin.math.pow

class CryptoRepository(
    private val marketApi: CoinGeckoApi,
    private val walletApi: BlockchairApi,
    private val signalEngine: SignalEngine
) {

    suspend fun analyzeAssets(
        assets: List<CryptoAsset>,
        walletAddresses: WalletAddresses
    ): List<AssetAnalysis> = coroutineScope {
        val marketData = marketApi.getMarkets(ids = assets.joinToString(",") { it.id })
            .associateBy { it.id }

        assets.map { asset ->
            async {
                val history = runCatching {
                    marketApi.getMarketChart(id = asset.id).prices
                        .mapNotNull { row ->
                            val millis = row.getOrNull(0)?.toLong() ?: return@mapNotNull null
                            val price = row.getOrNull(1) ?: return@mapNotNull null
                            PricePoint(timestamp = Instant.ofEpochMilli(millis), priceUsd = price)
                        }
                }.getOrDefault(emptyList())

                val (signals, finalAction) = signalEngine.analyze(history)

                val balance = fetchBalance(asset, walletAddresses)

                AssetAnalysis(
                    asset = asset,
                    currentPriceUsd = marketData[asset.id]?.current_price ?: 0.0,
                    balance = balance,
                    history = history,
                    algorithmSignals = signals,
                    finalAction = if (history.isEmpty()) TradeAction.HOLD else finalAction
                )
            }
        }.awaitAll()
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
