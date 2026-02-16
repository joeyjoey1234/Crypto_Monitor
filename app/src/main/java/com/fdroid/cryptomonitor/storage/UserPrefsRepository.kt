package com.fdroid.cryptomonitor.storage

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fdroid.cryptomonitor.data.model.WalletAddresses
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "crypto_monitor_prefs")

class UserPrefsRepository(private val context: Context) {

    private object Keys {
        val BitcoinAddress = stringPreferencesKey("wallet_address_bitcoin")
        val EthereumAddress = stringPreferencesKey("wallet_address_ethereum")
        val SolanaAddress = stringPreferencesKey("wallet_address_solana")
        val DogecoinAddress = stringPreferencesKey("wallet_address_dogecoin")
        val CardanoAddress = stringPreferencesKey("wallet_address_cardano")
    }

    val walletAddresses: Flow<WalletAddresses> = context.dataStore.data.map { prefs ->
        WalletAddresses(
            bitcoin = prefs[Keys.BitcoinAddress].orEmpty(),
            ethereum = prefs[Keys.EthereumAddress].orEmpty(),
            solana = prefs[Keys.SolanaAddress].orEmpty(),
            dogecoin = prefs[Keys.DogecoinAddress].orEmpty(),
            cardano = prefs[Keys.CardanoAddress].orEmpty()
        )
    }

    suspend fun saveWalletAddresses(addresses: WalletAddresses) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[Keys.BitcoinAddress] = addresses.bitcoin.trim()
            prefs[Keys.EthereumAddress] = addresses.ethereum.trim()
            prefs[Keys.SolanaAddress] = addresses.solana.trim()
            prefs[Keys.DogecoinAddress] = addresses.dogecoin.trim()
            prefs[Keys.CardanoAddress] = addresses.cardano.trim()
        }
    }
}
