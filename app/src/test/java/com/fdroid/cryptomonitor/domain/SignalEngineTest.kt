package com.fdroid.cryptomonitor.domain

import com.fdroid.cryptomonitor.data.model.TradeAction
import com.fdroid.cryptomonitor.data.model.AlgorithmSignal
import com.fdroid.cryptomonitor.data.model.PricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SignalEngineTest {

    private val engine = SignalEngine()

    @Test
    fun `returns hold when insufficient data`() {
        val history = (0 until 10).map {
            PricePoint(Instant.ofEpochSecond(it.toLong()), it + 1.0)
        }

        val (signals, finalAction) = engine.analyze(history)

        assertEquals(1, signals.size)
        assertEquals(TradeAction.HOLD, finalAction)
    }

    @Test
    fun `returns five algorithm signals when enough data`() {
        val history = (0 until 80).map {
            val base = 100.0 + it * 0.8
            val noise = if (it % 2 == 0) 1.2 else -1.2
            PricePoint(Instant.ofEpochSecond(it.toLong()), base + noise)
        }

        val (signals, _) = engine.analyze(history)
        val names = signals.map { it.algorithm }.toSet()

        assertEquals(5, signals.size)
        assertTrue(names.contains("SMA Crossover"))
        assertTrue(names.contains("RSI"))
        assertTrue(names.contains("MACD"))
        assertTrue(names.contains("Bollinger Bands"))
        assertTrue(names.contains("Rate of Change"))
    }

    @Test
    fun `vote engine returns buy when buy count leads by two`() {
        val finalAction = engine.decideFinalAction(
            listOf(
                AlgorithmSignal("A", TradeAction.BUY, ""),
                AlgorithmSignal("B", TradeAction.BUY, ""),
                AlgorithmSignal("C", TradeAction.BUY, ""),
                AlgorithmSignal("D", TradeAction.SELL, ""),
                AlgorithmSignal("E", TradeAction.HOLD, "")
            )
        )
        assertEquals(TradeAction.BUY, finalAction)
    }

    @Test
    fun `vote engine returns sell when sell count leads by two`() {
        val finalAction = engine.decideFinalAction(
            listOf(
                AlgorithmSignal("A", TradeAction.SELL, ""),
                AlgorithmSignal("B", TradeAction.SELL, ""),
                AlgorithmSignal("C", TradeAction.SELL, ""),
                AlgorithmSignal("D", TradeAction.BUY, ""),
                AlgorithmSignal("E", TradeAction.HOLD, "")
            )
        )
        assertEquals(TradeAction.SELL, finalAction)
    }

    @Test
    fun `vote engine returns hold on close vote`() {
        val finalAction = engine.decideFinalAction(
            listOf(
                AlgorithmSignal("A", TradeAction.BUY, ""),
                AlgorithmSignal("B", TradeAction.SELL, ""),
                AlgorithmSignal("C", TradeAction.HOLD, ""),
                AlgorithmSignal("D", TradeAction.HOLD, ""),
                AlgorithmSignal("E", TradeAction.HOLD, "")
            )
        )
        assertEquals(TradeAction.HOLD, finalAction)
    }
}
