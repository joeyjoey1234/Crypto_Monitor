package com.fdroid.cryptomonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import com.fdroid.cryptomonitor.data.remote.ApiFactory
import com.fdroid.cryptomonitor.data.repo.CryptoRepository
import com.fdroid.cryptomonitor.domain.SignalEngine
import com.fdroid.cryptomonitor.storage.UserPrefsRepository
import com.fdroid.cryptomonitor.ui.MainScreen
import com.fdroid.cryptomonitor.ui.MainViewModel
import com.fdroid.cryptomonitor.update.AppUpdateChecker

class MainActivity : ComponentActivity() {

    companion object {
        private const val NOTIFICATION_REQUEST_CODE = 1001
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            repository = CryptoRepository(
                marketApi = ApiFactory.coinGeckoApi,
                walletApi = ApiFactory.blockchairApi,
                signalEngine = SignalEngine()
            ),
            prefsRepository = UserPrefsRepository(this),
            updateChecker = AppUpdateChecker(
                releaseApi = ApiFactory.gitHubReleaseApi,
                owner = "joeyjoey1234",
                repo = "Crypto_Monitor"
            ),
            currentVersionName = appVersionName()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                MainScreen(viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_REQUEST_CODE
        )
    }

    private fun appVersionName(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return packageInfo.versionName ?: "0.0.0"
    }
}
