package com.fdroid.cryptomonitor.domain

import com.fdroid.cryptomonitor.data.model.AlgorithmSignal
import com.fdroid.cryptomonitor.data.model.PricePoint
import com.fdroid.cryptomonitor.data.model.TradeAction
import kotlin.math.pow
import kotlin.math.sqrt

class SignalEngine {

    fun analyze(history: List<PricePoint>): Pair<List<AlgorithmSignal>, TradeAction> {
        if (history.size < 30) {
            val signal = AlgorithmSignal(
                algorithm = "Data Check",
                action = TradeAction.HOLD,
                reason = "Not enough price points yet"
            )
            return listOf(signal) to TradeAction.HOLD
        }

        val closes = history.map { it.priceUsd }
        val signals = listOf(
            smaCrossover(closes),
            rsiSignal(closes),
            macdSignal(closes),
            bollingerSignal(closes),
            momentumSignal(closes)
        )

        return signals to decideFinalAction(signals)
    }

    internal fun decideFinalAction(signals: List<AlgorithmSignal>): TradeAction {
        val buys = signals.count { it.action == TradeAction.BUY }
        val sells = signals.count { it.action == TradeAction.SELL }
        return when {
            buys - sells >= 2 -> TradeAction.BUY
            sells - buys >= 2 -> TradeAction.SELL
            else -> TradeAction.HOLD
        }
    }

    private fun smaCrossover(closes: List<Double>, shortPeriod: Int = 7, longPeriod: Int = 25): AlgorithmSignal {
        val short = sma(closes.takeLast(shortPeriod))
        val long = sma(closes.takeLast(longPeriod))
        return when {
            short > long -> AlgorithmSignal("SMA Crossover", TradeAction.BUY, "Short SMA above long SMA")
            short < long -> AlgorithmSignal("SMA Crossover", TradeAction.SELL, "Short SMA below long SMA")
            else -> AlgorithmSignal("SMA Crossover", TradeAction.HOLD, "SMAs converged")
        }
    }

    private fun rsiSignal(closes: List<Double>, period: Int = 14): AlgorithmSignal {
        val rsi = rsi(closes, period)
        return when {
            rsi < 30.0 -> AlgorithmSignal("RSI", TradeAction.BUY, "RSI=$rsi indicates oversold")
            rsi > 70.0 -> AlgorithmSignal("RSI", TradeAction.SELL, "RSI=$rsi indicates overbought")
            else -> AlgorithmSignal("RSI", TradeAction.HOLD, "RSI=$rsi is neutral")
        }
    }

    private fun macdSignal(closes: List<Double>): AlgorithmSignal {
        val ema12 = emaSeries(closes, 12)
        val ema26 = emaSeries(closes, 26)
        val macd = ema12.zip(ema26).map { (a, b) -> a - b }
        val signalLine = emaSeries(macd, 9)

        val macdPrev = macd[macd.lastIndex - 1]
        val macdNow = macd.last()
        val sigPrev = signalLine[signalLine.lastIndex - 1]
        val sigNow = signalLine.last()

        return when {
            macdPrev <= sigPrev && macdNow > sigNow -> {
                AlgorithmSignal("MACD", TradeAction.BUY, "MACD crossed above signal line")
            }
            macdPrev >= sigPrev && macdNow < sigNow -> {
                AlgorithmSignal("MACD", TradeAction.SELL, "MACD crossed below signal line")
            }
            else -> AlgorithmSignal("MACD", TradeAction.HOLD, "No recent MACD crossover")
        }
    }

    private fun bollingerSignal(closes: List<Double>, period: Int = 20): AlgorithmSignal {
        val window = closes.takeLast(period)
        val middle = sma(window)
        val stdDev = stdDev(window)
        val upper = middle + 2.0 * stdDev
        val lower = middle - 2.0 * stdDev
        val current = closes.last()

        return when {
            current < lower -> AlgorithmSignal("Bollinger Bands", TradeAction.BUY, "Price below lower band")
            current > upper -> AlgorithmSignal("Bollinger Bands", TradeAction.SELL, "Price above upper band")
            else -> AlgorithmSignal("Bollinger Bands", TradeAction.HOLD, "Price inside bands")
        }
    }

    private fun momentumSignal(closes: List<Double>, period: Int = 10): AlgorithmSignal {
        val current = closes.last()
        val prior = closes[closes.lastIndex - period]
        if (prior == 0.0) {
            return AlgorithmSignal("Rate of Change", TradeAction.HOLD, "Insufficient reference")
        }

        val roc = ((current - prior) / prior) * 100.0
        return when {
            roc > 5.0 -> AlgorithmSignal("Rate of Change", TradeAction.BUY, "Momentum strong ($roc%)")
            roc < -5.0 -> AlgorithmSignal("Rate of Change", TradeAction.SELL, "Momentum weak ($roc%)")
            else -> AlgorithmSignal("Rate of Change", TradeAction.HOLD, "Momentum mild ($roc%)")
        }
    }

    private fun sma(values: List<Double>): Double = values.average()

    private fun stdDev(values: List<Double>): Double {
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        return sqrt(variance)
    }

    private fun emaSeries(values: List<Double>, period: Int): List<Double> {
        val alpha = 2.0 / (period + 1)
        val result = MutableList(values.size) { 0.0 }
        result[0] = values.first()
        for (i in 1 until values.size) {
            result[i] = alpha * values[i] + (1 - alpha) * result[i - 1]
        }
        return result
    }

    private fun rsi(values: List<Double>, period: Int): Double {
        var gain = 0.0
        var loss = 0.0

        for (i in values.lastIndex - period + 1..values.lastIndex) {
            val delta = values[i] - values[i - 1]
            if (delta >= 0) {
                gain += delta
            } else {
                loss -= delta
            }
        }

        if (loss == 0.0) return 100.0
        val rs = (gain / period) / (loss / period)
        return 100.0 - (100.0 / (1 + rs))
    }
}
