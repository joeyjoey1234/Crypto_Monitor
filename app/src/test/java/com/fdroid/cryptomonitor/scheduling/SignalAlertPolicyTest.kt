package com.fdroid.cryptomonitor.scheduling

import com.fdroid.cryptomonitor.data.model.TradeAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalAlertPolicyTest {

    @Test
    fun `should attempt notification only for changed non-hold actions`() {
        assertFalse(SignalAlertPolicy.shouldAttemptNotification(TradeAction.HOLD, TradeAction.HOLD.name))
        assertTrue(SignalAlertPolicy.shouldAttemptNotification(TradeAction.BUY, TradeAction.HOLD.name))
        assertFalse(SignalAlertPolicy.shouldAttemptNotification(TradeAction.SELL, TradeAction.SELL.name))
    }

    @Test
    fun `should cache hold always and buy sell only when delivered`() {
        assertTrue(SignalAlertPolicy.shouldCacheAction(TradeAction.HOLD, delivered = false))
        assertTrue(SignalAlertPolicy.shouldCacheAction(TradeAction.BUY, delivered = true))
        assertFalse(SignalAlertPolicy.shouldCacheAction(TradeAction.BUY, delivered = false))
    }
}
