package com.fdroid.cryptomonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fdroid.cryptomonitor.data.model.DefaultAssets
import com.fdroid.cryptomonitor.data.repo.CryptoRepository
import com.fdroid.cryptomonitor.storage.UserPrefsRepository
import com.fdroid.cryptomonitor.update.AppUpdateChecker
import com.fdroid.cryptomonitor.update.UpdateCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

class MainViewModel(
    private val repository: CryptoRepository,
    private val prefsRepository: UserPrefsRepository,
    private val updateChecker: AppUpdateChecker,
    private val currentVersionName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(isLoading = true))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            prefsRepository.walletAddresses.distinctUntilChanged().collect { addresses ->
                _uiState.update { it.copy(walletAddresses = addresses) }
                refresh()
            }
        }
        checkForUpdates()
    }

    fun onWalletAddressChanged(chain: String, value: String) {
        _uiState.update { state ->
            val wallets = state.walletAddresses
            val updated = when (chain) {
                "bitcoin" -> wallets.copy(bitcoin = value)
                "ethereum" -> wallets.copy(ethereum = value)
                "solana" -> wallets.copy(solana = value)
                "dogecoin" -> wallets.copy(dogecoin = value)
                "cardano" -> wallets.copy(cardano = value)
                else -> wallets
            }
            state.copy(walletAddresses = updated)
        }
    }

    fun saveWalletAddresses() {
        viewModelScope.launch {
            prefsRepository.saveWalletAddresses(_uiState.value.walletAddresses)
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = runCatching {
                repository.analyzeAssets(
                    assets = DefaultAssets,
                    walletAddresses = _uiState.value.walletAddresses
                )
            }

            result.onSuccess { analyses ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        analyses = analyses,
                        lastUpdated = Instant.now(),
                        error = null
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "Failed to refresh data"
                    )
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingUpdate = true,
                    updateStatusMessage = null
                )
            }

            when (val result = updateChecker.checkForUpdate(currentVersionName)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            availableUpdate = result.info,
                            updateStatusMessage = "Update available: ${result.info.versionTag}"
                        )
                    }
                }
                is UpdateCheckResult.NoUpdate -> {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            availableUpdate = null,
                            updateStatusMessage = "App is up to date (${result.latestTag})"
                        )
                    }
                }
                is UpdateCheckResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateStatusMessage = "Update check failed: ${result.reason}"
                        )
                    }
                }
            }
        }
    }

    fun onUpdateInstallStarted() {
        _uiState.update { it.copy(isInstallingUpdate = true, updateStatusMessage = "Downloading update...") }
    }

    fun onUpdateInstallFinished(error: String?) {
        _uiState.update {
            it.copy(
                isInstallingUpdate = false,
                updateStatusMessage = error ?: "Installer launched"
            )
        }
    }

    class Factory(
        private val repository: CryptoRepository,
        private val prefsRepository: UserPrefsRepository,
        private val updateChecker: AppUpdateChecker,
        private val currentVersionName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                repository = repository,
                prefsRepository = prefsRepository,
                updateChecker = updateChecker,
                currentVersionName = currentVersionName
            ) as T
        }
    }
}
