package com.fdroid.cryptomonitor.ui

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Crypto Signal Monitor") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.walletAddresses.bitcoin,
                    onValueChange = { viewModel.onWalletAddressChanged("bitcoin", it) },
                    label = { Text("Bitcoin Address (BTC)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Only checked for BTC balance") }
                )
            }
            item {
                OutlinedTextField(
                    value = state.walletAddresses.ethereum,
                    onValueChange = { viewModel.onWalletAddressChanged("ethereum", it) },
                    label = { Text("Ethereum Address (ETH)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = state.walletAddresses.solana,
                    onValueChange = { viewModel.onWalletAddressChanged("solana", it) },
                    label = { Text("Solana Address (SOL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = state.walletAddresses.dogecoin,
                    onValueChange = { viewModel.onWalletAddressChanged("dogecoin", it) },
                    label = { Text("Dogecoin Address (DOGE)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = state.walletAddresses.cardano,
                    onValueChange = { viewModel.onWalletAddressChanged("cardano", it) },
                    label = { Text("Cardano Address (ADA)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row {
                    Button(onClick = viewModel::saveWalletAddresses) {
                        Text("Save Addresses")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = viewModel::refresh) {
                        Text("Refresh Now")
                    }
                }
            }
            item {
                Row {
                    Button(onClick = viewModel::checkForUpdates, enabled = !state.isCheckingUpdate) {
                        Text(if (state.isCheckingUpdate) "Checking..." else "Check App Update")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    state.availableUpdate?.let { update ->
                        Button(
                            enabled = !state.isInstallingUpdate,
                            onClick = {
                                scope.launch {
                                    viewModel.onUpdateInstallStarted()
                                    val error = runCatching {
                                        val apk = ApkUpdateInstaller.downloadApk(context, update)
                                        ApkUpdateInstaller.installApk(context, apk)
                                    }.exceptionOrNull()?.message
                                    viewModel.onUpdateInstallFinished(error)
                                }
                            }
                        ) {
                            Text(if (state.isInstallingUpdate) "Downloading..." else "Install ${update.versionTag}")
                        }
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
                    Text("Release: ${update.releaseName}", style = MaterialTheme.typography.bodySmall)
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
            items(state.analyses, key = { it.asset.id }) { analysis ->
                AssetCard(analysis = analysis, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
