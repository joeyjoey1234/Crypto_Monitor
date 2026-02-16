package com.fdroid.cryptomonitor.data.repo

import com.fdroid.cryptomonitor.data.model.CryptoAsset
import com.fdroid.cryptomonitor.data.model.WalletAddresses
import com.fdroid.cryptomonitor.data.remote.BlockchairAddressContainer
import com.fdroid.cryptomonitor.data.remote.BlockchairAddressInfo
import com.fdroid.cryptomonitor.data.remote.BlockchairApi
import com.fdroid.cryptomonitor.data.remote.BlockchairResponse
import com.fdroid.cryptomonitor.data.remote.CoinGeckoApi
import com.fdroid.cryptomonitor.data.remote.MarketChartDto
import com.fdroid.cryptomonitor.data.remote.MarketCoinDto
import com.fdroid.cryptomonitor.domain.SignalEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoRepositoryTest {

    @Test
    fun `uses chain specific addresses and skips chains without address`() = runTest {
        val marketApi = FakeMarketApi()
        val walletApi = FakeWalletApi(
            balancesByAddress = mapOf(
                "btc_addr" to 150000000.0
            )
        )
        val repository = CryptoRepository(marketApi, walletApi, SignalEngine())

        val assets = listOf(
            CryptoAsset("bitcoin", "BTC", "Bitcoin", "bitcoin", 8),
            CryptoAsset("solana", "SOL", "Solana", "solana", 9)
        )

        val analyses = repository.analyzeAssets(
            assets = assets,
            walletAddresses = WalletAddresses(bitcoin = "btc_addr")
        )

        assertEquals(1, walletApi.calls.size)
        assertEquals("bitcoin:btc_addr", walletApi.calls.single())
        assertEquals(1.5, analyses.first { it.asset.symbol == "BTC" }.balance ?: 0.0, 0.000001)
        assertNull(analyses.first { it.asset.symbol == "SOL" }.balance)
    }

    private class FakeMarketApi : CoinGeckoApi {
        override suspend fun getMarkets(
            vsCurrency: String,
            ids: String,
            changeWindow: String
        ): List<MarketCoinDto> {
            return ids.split(",").mapIndexed { index, id ->
                MarketCoinDto(id = id, symbol = id.take(3), name = id, current_price = 100.0 + index)
            }
        }

        override suspend fun getMarketChart(
            id: String,
            vsCurrency: String,
            days: Int,
            interval: String
        ): MarketChartDto {
            val points = (0 until 80).map { idx ->
                listOf((1700000000000L + idx * 3600000L).toDouble(), 100.0 + idx)
            }
            return MarketChartDto(prices = points)
        }
    }

    private class FakeWalletApi(
        private val balancesByAddress: Map<String, Double>
    ) : BlockchairApi {
        val calls = mutableListOf<String>()

        override suspend fun getAddress(chain: String, address: String): BlockchairResponse {
            calls += "$chain:$address"
            val balance = balancesByAddress[address]
            val container = BlockchairAddressContainer(address = BlockchairAddressInfo(balance = balance))
            return BlockchairResponse(data = mapOf(address to container))
        }
    }
}
