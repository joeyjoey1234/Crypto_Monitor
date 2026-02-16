package com.fdroid.cryptomonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fdroid.cryptomonitor.data.model.WalletAddresses
import com.fdroid.cryptomonitor.data.repo.CryptoRepository
import com.fdroid.cryptomonitor.domain.WalletAddressDetector
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
import retrofit2.HttpException
import java.io.IOException
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
    private var lastRefreshAtMillis: Long = 0L

    init {
        viewModelScope.launch {
            prefsRepository.walletAddresses.distinctUntilChanged().collect { addresses ->
                _uiState.update { it.copy(walletAddresses = addresses) }
                refresh(force = true)
            }
        }
        checkForUpdates()
    }

    fun onWalletInputChanged(value: String) {
        _uiState.update { it.copy(walletInput = value, walletStatusMessage = null) }
    }

    fun addWalletAddressFromInput() {
        val input = _uiState.value.walletInput.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(walletStatusMessage = "Enter an address first") }
            return
        }

        val chain = WalletAddressDetector.detectChain(input)
        if (chain == null) {
            _uiState.update {
                it.copy(walletStatusMessage = "Unsupported or invalid address format")
            }
            return
        }

        val updated = updateWalletAddresses(_uiState.value.walletAddresses, chain, input)
        if (updated == _uiState.value.walletAddresses) {
            _uiState.update {
                it.copy(walletStatusMessage = "Address already saved for ${chainLabel(chain)}")
            }
            return
        }

        viewModelScope.launch {
            prefsRepository.saveWalletAddresses(updated)
            _uiState.update {
                it.copy(
                    walletAddresses = updated,
                    walletInput = "",
                    walletStatusMessage = "Saved as ${chainLabel(chain)} address"
                )
            }
        }
    }

    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAtMillis < 20_000L) {
            _uiState.update {
                it.copy(error = "Too many refreshes. Please wait a few seconds before retrying.")
            }
            return
        }
        lastRefreshAtMillis = now

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = runCatching {
                val trackedAssets = repository.resolveTrackedAssets(_uiState.value.walletAddresses)
                repository.analyzeAssets(
                    assets = trackedAssets,
                    walletAddresses = _uiState.value.walletAddresses
                )
            }

            result.onSuccess { analyses ->
                val ownedAnalyses = analyses.filter { (it.balance ?: 0.0) > 0.0 }
                val totalPortfolioUsd = ownedAnalyses.sumOf { (it.balance ?: 0.0) * it.currentPriceUsd }
                val dayChangeUsd = ownedAnalyses.sumOf { analysis ->
                    val positionUsd = (analysis.balance ?: 0.0) * analysis.currentPriceUsd
                    positionUsd * ((analysis.priceChange24hPct ?: 0.0) / 100.0)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        analyses = ownedAnalyses,
                        totalPortfolioUsd = totalPortfolioUsd,
                        dayChangeUsd = dayChangeUsd,
                        lastUpdated = Instant.now(),
                        error = null
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        analyses = emptyList(),
                        totalPortfolioUsd = 0.0,
                        dayChangeUsd = 0.0,
                        error = mapRefreshError(throwable)
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

    private fun mapRefreshError(error: Throwable): String {
        return when (error) {
            is HttpException -> {
                if (error.code() == 429) {
                    "Rate limit reached by the market API. Please wait 1-2 minutes and try again."
                } else {
                    "API request failed (${error.code()})"
                }
            }

            is IOException -> "Network error while refreshing data"
            else -> error.message ?: "Failed to refresh data"
        }
    }

    private fun updateWalletAddresses(
        current: WalletAddresses,
        chain: String,
        address: String
    ): WalletAddresses {
        return when (chain) {
            "evm" -> current.copy(ethereum = address, base = address)
            "bitcoin" -> current.copy(bitcoin = address)
            "ethereum" -> current.copy(ethereum = address)
            "base" -> current.copy(base = address)
            "solana" -> current.copy(solana = address)
            "dogecoin" -> current.copy(dogecoin = address)
            "cardano" -> current.copy(cardano = address)
            else -> current
        }
    }

    private fun chainLabel(chain: String): String {
        return when (chain) {
            "evm" -> "EVM (Ethereum/Base)"
            "bitcoin" -> "Bitcoin"
            "ethereum" -> "Ethereum"
            "base" -> "Base"
            "solana" -> "Solana"
            "dogecoin" -> "Dogecoin"
            "cardano" -> "Cardano"
            else -> chain
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
