package com.fdroid.cryptomonitor.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fdroid.cryptomonitor.data.model.TradeAction
import com.fdroid.cryptomonitor.data.remote.ApiFactory
import com.fdroid.cryptomonitor.data.repo.CryptoRepository
import com.fdroid.cryptomonitor.domain.SignalEngine
import com.fdroid.cryptomonitor.storage.UserPrefsRepository
import kotlinx.coroutines.flow.first

class SignalCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val repository = CryptoRepository(
        marketApi = ApiFactory.coinGeckoApi,
        walletApi = ApiFactory.blockchairApi,
        signalEngine = SignalEngine()
    )

    override suspend fun doWork(): Result {
        return runCatching {
            val prefs = UserPrefsRepository(applicationContext)
            val walletAddresses = prefs.walletAddresses.first()
            val trackedAssets = repository.resolveTrackedAssets(walletAddresses)
            if (trackedAssets.isEmpty()) return@runCatching

            val analyses = repository
                .analyzeAssets(trackedAssets, walletAddresses)
                .filter { (it.balance ?: 0.0) > 0.0 }

            val sharedPrefs = applicationContext.getSharedPreferences("signal_alert_cache", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()

            analyses.forEach { analysis ->
                val action = analysis.finalAction
                val key = "${analysis.asset.id}_action"
                val previous = sharedPrefs.getString(key, TradeAction.HOLD.name)

                if (SignalAlertPolicy.shouldAttemptNotification(action, previous)) {
                    val title = "${analysis.asset.symbol}: ${action.name} signal"
                    val message = "${analysis.asset.displayName} flagged ${action.name} by multi-algorithm vote."
                    val delivered = NotificationHelper.notifySignal(
                        context = applicationContext,
                        notificationId = analysis.asset.id.hashCode(),
                        title = title,
                        message = message
                    )
                    if (SignalAlertPolicy.shouldCacheAction(action, delivered)) {
                        editor.putString(key, action.name)
                    }
                } else if (SignalAlertPolicy.shouldCacheAction(action, delivered = false)) {
                    editor.putString(key, action.name)
                }
            }

            editor.apply()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
