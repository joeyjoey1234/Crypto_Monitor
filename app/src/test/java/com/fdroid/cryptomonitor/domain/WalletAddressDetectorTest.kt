package com.fdroid.cryptomonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletAddressDetectorTest {

    @Test
    fun `detects ethereum address`() {
        val chain = WalletAddressDetector.detectChain("0x742d35Cc6634C0532925a3b844Bc454e4438f44e")
        assertEquals("ethereum", chain)
    }

    @Test
    fun `detects bitcoin bech32 address`() {
        val chain = WalletAddressDetector.detectChain("bc1qw4h8x6xjv9rwlyf0j4j2xgu4m8vs8m2s34ux3m")
        assertEquals("bitcoin", chain)
    }

    @Test
    fun `detects cardano address`() {
        val chain = WalletAddressDetector.detectChain("addr1q9d6k6zqk8n45x8m3r4y8v45q3m6w7n2x3c9t5k6v9r8s7n")
        assertEquals("cardano", chain)
    }

    @Test
    fun `returns null for unsupported format`() {
        val chain = WalletAddressDetector.detectChain("not-a-real-wallet")
        assertNull(chain)
    }
}
