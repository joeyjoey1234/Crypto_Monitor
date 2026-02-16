package com.fdroid.cryptomonitor.data.model

import java.time.Instant

enum class TradeAction {
    BUY,
    SELL,
    HOLD
}

data class CryptoAsset(
    val id: String,
    val symbol: String,
    val displayName: String,
    val chain: String,
    val decimals: Int
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
    val balance: Double?,
    val history: List<PricePoint>,
    val algorithmSignals: List<AlgorithmSignal>,
    val finalAction: TradeAction
)

data class WalletAddresses(
    val bitcoin: String = "",
    val ethereum: String = "",
    val solana: String = "",
    val dogecoin: String = "",
    val cardano: String = ""
) {
    fun forChain(chain: String): String? {
        val value = when (chain) {
            "bitcoin" -> bitcoin
            "ethereum" -> ethereum
            "solana" -> solana
            "dogecoin" -> dogecoin
            "cardano" -> cardano
            else -> ""
        }
        return value.trim().ifBlank { null }
    }
}

val DefaultAssets = listOf(
    CryptoAsset("bitcoin", "BTC", "Bitcoin", "bitcoin", 8),
    CryptoAsset("ethereum", "ETH", "Ethereum", "ethereum", 18),
    CryptoAsset("solana", "SOL", "Solana", "solana", 9),
    CryptoAsset("dogecoin", "DOGE", "Dogecoin", "dogecoin", 8),
    CryptoAsset("cardano", "ADA", "Cardano", "cardano", 6)
)
