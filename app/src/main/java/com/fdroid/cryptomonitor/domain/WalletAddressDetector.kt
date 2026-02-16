package com.fdroid.cryptomonitor.domain

object WalletAddressDetector {

    fun detectChain(address: String): String? {
        val normalized = address.trim()
        if (normalized.isEmpty()) return null

        return when {
            normalized.matches(Regex("^0x[a-fA-F0-9]{40}$")) -> "ethereum"
            normalized.matches(Regex("^addr1[0-9a-z]{20,}$")) -> "cardano"
            normalized.matches(Regex("^D[5-9A-HJ-NP-U][1-9A-HJ-NP-Za-km-z]{32}$")) -> "dogecoin"
            normalized.matches(Regex("^(bc1[ac-hj-np-z02-9]{11,71}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})$")) -> "bitcoin"
            normalized.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")) -> "solana"
            else -> null
        }
    }
}
