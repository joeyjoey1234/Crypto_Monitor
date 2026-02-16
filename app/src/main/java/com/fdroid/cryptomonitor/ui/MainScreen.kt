package com.fdroid.cryptomonitor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fdroid.cryptomonitor.ui.components.AssetCard
import com.fdroid.cryptomonitor.update.ApkUpdateInstaller
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Settings" else "Crypto Signal Monitor") },
                actions = {
                    TextButton(onClick = { showSettings = !showSettings }) {
                        Text(if (showSettings) "Dashboard" else "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (showSettings) {
            SettingsContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = state,
                onCheckUpdates = viewModel::checkForUpdates,
                onInstall = {
                    val update = state.availableUpdate ?: return@SettingsContent
                    scope.launch {
                        viewModel.onUpdateInstallStarted()
                        val error = runCatching {
                            val apk = ApkUpdateInstaller.downloadApk(context, update)
                            ApkUpdateInstaller.installApk(context, apk)
                        }.exceptionOrNull()?.message
                        viewModel.onUpdateInstallFinished(error)
                    }
                },
                onDonate = {
                    val donateIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://buymeacoffee.com/joejoe1234")
                    )
                    context.startActivity(donateIntent)
                }
            )
        } else {
            DashboardContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = state,
                formatter = formatter,
                onWalletInputChanged = viewModel::onWalletInputChanged,
                onAddAddress = viewModel::addWalletAddressFromInput,
                onRefresh = { viewModel.refresh(force = false) }
            )
        }
    }
}

@Composable
private fun DashboardContent(
    modifier: Modifier,
    state: MainUiState,
    formatter: DateTimeFormatter,
    onWalletInputChanged: (String) -> Unit,
    onAddAddress: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.walletInput,
                onValueChange = onWalletInputChanged,
                label = { Text("Wallet Address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Paste one address. App auto-detects network type.") }
            )
        }
        item {
            Row {
                Button(onClick = onAddAddress) {
                    Text("Add Address")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
        }
        item {
            state.walletStatusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            val addressRows = listOf(
                "BTC" to state.walletAddresses.bitcoin,
                "ETH" to state.walletAddresses.ethereum,
                "BASE" to state.walletAddresses.base,
                "SOL" to state.walletAddresses.solana,
                "DOGE" to state.walletAddresses.dogecoin,
                "ADA" to state.walletAddresses.cardano
            ).filter { it.second.isNotBlank() }

            if (addressRows.isNotEmpty()) {
                Text("Saved wallet addresses:", style = MaterialTheme.typography.titleSmall)
                addressRows.forEach { (symbol, address) ->
                    Text("$symbol: $address", style = MaterialTheme.typography.bodySmall)
                }
            } else if (!state.isLoading) {
                Text(
                    "Add a wallet address to start monitoring and charting that asset.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            state.lastUpdated?.let {
                val localTime = formatter.format(it.atZone(ZoneId.systemDefault()))
                Text("Last updated: $localTime", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            val hasAnyAddress = state.walletAddresses.bitcoin.isNotBlank() ||
                state.walletAddresses.ethereum.isNotBlank() ||
                state.walletAddresses.base.isNotBlank() ||
                state.walletAddresses.solana.isNotBlank() ||
                state.walletAddresses.dogecoin.isNotBlank() ||
                state.walletAddresses.cardano.isNotBlank()
            if (hasAnyAddress && !state.isLoading && state.error == null && state.analyses.isEmpty()) {
                Text(
                    "No non-zero balances detected for tracked assets on saved addresses.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        items(state.analyses, key = { it.asset.id }) { analysis ->
            AssetCard(analysis = analysis, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier,
    state: MainUiState,
    onCheckUpdates: () -> Unit,
    onInstall: () -> Unit,
    onDonate: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("App Updates", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row {
                Button(onClick = onCheckUpdates, enabled = !state.isCheckingUpdate) {
                    Text(if (state.isCheckingUpdate) "Checking..." else "Check for Update")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onInstall,
                    enabled = state.availableUpdate != null && !state.isInstallingUpdate
                ) {
                    Text(if (state.isInstallingUpdate) "Downloading..." else "Install Update")
                }
            }
        }
        item {
            state.updateStatusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            state.availableUpdate?.let { update ->
                Text("Latest release: ${update.releaseName}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text("Support", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Button(onClick = onDonate) {
                Text("Donate (Buy Me a Coffee)")
            }
        }
    }
}
