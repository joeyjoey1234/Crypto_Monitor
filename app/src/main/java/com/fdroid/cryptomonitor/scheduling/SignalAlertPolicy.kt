package com.fdroid.cryptomonitor.scheduling

import com.fdroid.cryptomonitor.data.model.TradeAction

object SignalAlertPolicy {
    fun shouldAttemptNotification(action: TradeAction, previousAction: String?): Boolean {
        return action != TradeAction.HOLD && previousAction != action.name
    }

    fun shouldCacheAction(action: TradeAction, delivered: Boolean): Boolean {
        return action == TradeAction.HOLD || delivered
    }
}
