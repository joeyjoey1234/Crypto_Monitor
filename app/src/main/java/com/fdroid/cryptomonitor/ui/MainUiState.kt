package com.fdroid.cryptomonitor.ui

import com.fdroid.cryptomonitor.data.model.AssetAnalysis
import com.fdroid.cryptomonitor.data.model.WalletAddresses
import com.fdroid.cryptomonitor.update.AppUpdateInfo
import java.time.Instant

data class MainUiState(
    val isLoading: Boolean = false,
    val walletAddresses: WalletAddresses = WalletAddresses(),
    val analyses: List<AssetAnalysis> = emptyList(),
    val lastUpdated: Instant? = null,
    val error: String? = null,
    val isCheckingUpdate: Boolean = false,
    val isInstallingUpdate: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val updateStatusMessage: String? = null
)
