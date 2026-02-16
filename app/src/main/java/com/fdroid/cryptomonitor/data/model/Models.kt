package com.fdroid.cryptomonitor.data.model

import java.time.Instant

enum class TradeAction {
    BUY,
    SELL,
    HOLD
}

data class CryptoAsset(
    val id: String,
    val marketId: String = id,
    val symbol: String,
    val displayName: String,
    val chain: String,
    val decimals: Int,
    val usesNativeBalance: Boolean = true,
    val tokenContract: String? = null
)

data class PricePoint(
    val timestamp: Instant,
    val priceUsd: Double
)

data class AlgorithmSignal(
    val algorithm: String,
    val action: TradeAction,
    val reason: String
)

data class AssetAnalysis(
    val asset: CryptoAsset,
    val currentPriceUsd: Double,
    val priceChange24hPct: Double?,
    val balance: Double?,
    val history: List<PricePoint>,
    val algorithmSignals: List<AlgorithmSignal>,
    val finalAction: TradeAction
)

data class WalletAddresses(
    val bitcoin: String = "",
    val ethereum: String = "",
    val base: String = "",
    val solana: String = "",
    val dogecoin: String = "",
    val cardano: String = ""
) {
    fun forChain(chain: String): String? {
        val value = when (chain) {
            "bitcoin" -> bitcoin
            "ethereum" -> ethereum
            "base" -> base
            "solana" -> solana
            "dogecoin" -> dogecoin
            "cardano" -> cardano
            else -> ""
        }
        return value.trim().ifBlank { null }
    }
}

val DefaultAssets = listOf(
    CryptoAsset(id = "bitcoin", symbol = "BTC", displayName = "Bitcoin", chain = "bitcoin", decimals = 8),
    CryptoAsset(id = "ethereum", symbol = "ETH", displayName = "Ethereum", chain = "ethereum", decimals = 18),
    CryptoAsset(id = "solana", symbol = "SOL", displayName = "Solana", chain = "solana", decimals = 9),
    CryptoAsset(id = "dogecoin", symbol = "DOGE", displayName = "Dogecoin", chain = "dogecoin", decimals = 8),
    CryptoAsset(id = "cardano", symbol = "ADA", displayName = "Cardano", chain = "cardano", decimals = 6)
)
