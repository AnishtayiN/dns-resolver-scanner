package com.anishtayin.dnsscanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dns_scanner")

/** ذخیره‌ی محلی آخرین اسکن (JSON نتایج + زمان) و سرورهای دلخواه کاربر. */
class HistoryStore(private val context: Context) {

    private val lastScanJsonKey = stringPreferencesKey("last_scan_json")
    private val lastScanTimeKey = longPreferencesKey("last_scan_time")
    private val customIpsKey = stringPreferencesKey("custom_ips")

    suspend fun saveLastScan(json: String, scannedAtEpochMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[lastScanJsonKey] = json
            prefs[lastScanTimeKey] = scannedAtEpochMs
        }
    }

    suspend fun loadLastScan(): Pair<String?, Long?> {
        val prefs = context.dataStore.data.firstOrNull() ?: return null to null
        return prefs[lastScanJsonKey] to prefs[lastScanTimeKey]
    }

    suspend fun saveCustomIps(ips: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[customIpsKey] = ips.joinToString(",")
        }
    }

    suspend fun loadCustomIps(): List<String> {
        val prefs = context.dataStore.data.firstOrNull() ?: return emptyList()
        val raw = prefs[customIpsKey] ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
