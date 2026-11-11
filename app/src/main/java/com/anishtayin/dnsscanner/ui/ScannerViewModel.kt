package com.anishtayin.dnsscanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anishtayin.dnsscanner.data.HistoryStore
import com.anishtayin.dnsscanner.dns.BUILTIN_RESOLVERS
import com.anishtayin.dnsscanner.dns.Benchmark
import com.anishtayin.dnsscanner.dns.Resolver
import com.anishtayin.dnsscanner.dns.ResolverStatus
import com.anishtayin.dnsscanner.dns.ScanResult
import com.anishtayin.dnsscanner.dns.parseCustomIp
import com.anishtayin.dnsscanner.dns.resultsToJson
import com.anishtayin.dnsscanner.dns.resultsToText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val store = HistoryStore(application)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _progressDone = MutableStateFlow(0)
    val progressDone: StateFlow<Int> = _progressDone.asStateFlow()

    private val _progressTotal = MutableStateFlow(0)
    val progressTotal: StateFlow<Int> = _progressTotal.asStateFlow()

    private val _results = MutableStateFlow<List<ScanResult>>(emptyList())
    val results: StateFlow<List<ScanResult>> = _results.asStateFlow()

    private val _lastScanTime = MutableStateFlow<Long?>(null)
    val lastScanTime: StateFlow<Long?> = _lastScanTime.asStateFlow()

    private val _customIps = MutableStateFlow<List<String>>(emptyList())
    val customIps: StateFlow<List<String>> = _customIps.asStateFlow()

    private val _invalidIpAttempt = MutableStateFlow(false)
    val invalidIpAttempt: StateFlow<Boolean> = _invalidIpAttempt.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            _customIps.value = store.loadCustomIps()
            val (json, time) = store.loadLastScan()
            _lastScanTime.value = time
            if (json != null) _results.value = parseResultsJson(json)
        }
    }

    private fun allResolvers(): List<Resolver> {
        val custom = _customIps.value.map { Resolver("Custom", it, custom = true) }
        return BUILTIN_RESOLVERS + custom
    }

    fun scanAll() {
        if (_isScanning.value) return
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            _progressDone.value = 0
            val resolvers = allResolvers()
            _progressTotal.value = resolvers.size
            val scanned = Benchmark.runBenchmark(resolvers) { done, _ ->
                _progressDone.value = done
            }
            _results.value = scanned
            val now = System.currentTimeMillis()
            _lastScanTime.value = now
            store.saveLastScan(resultsToJson(scanned, now), now)
            _isScanning.value = false
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    /** افزودن سرور دلخواه؛ false یعنی IP نامعتبر است. */
    fun addCustomIp(input: String): Boolean {
        val ip = parseCustomIp(input) ?: run {
            _invalidIpAttempt.value = true
            return false
        }
        _invalidIpAttempt.value = false
        if (ip in _customIps.value) return true
        val updated = _customIps.value + ip
        _customIps.value = updated
        viewModelScope.launch { store.saveCustomIps(updated) }
        return true
    }

    fun clearInvalidIpFlag() {
        _invalidIpAttempt.value = false
    }

    fun removeCustomIp(ip: String) {
        val updated = _customIps.value - ip
        _customIps.value = updated
        viewModelScope.launch { store.saveCustomIps(updated) }
        _results.value = _results.value.filterNot { it.resolver.ip == ip }
    }

    fun exportText(): String = resultsToText(_results.value)

    fun exportJson(): String =
        resultsToJson(_results.value, _lastScanTime.value ?: System.currentTimeMillis())

    fun workingCount(): Int = _results.value.count { it.status == ResolverStatus.WORKING || it.status == ResolverStatus.SLOW }

    /** بازسازی نتایج از JSON ذخیره‌شده‌ی خودمان (فقط تاریخچه‌ی نمایشی). */
    private fun parseResultsJson(json: String): List<ScanResult> {
        return try {
            val root = JSONObject(json)
            val arr: JSONArray = root.getJSONArray("results")
            val out = mutableListOf<ScanResult>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ip = o.getString("ip")
                val provider = o.getString("provider")
                val builtin = BUILTIN_RESOLVERS.find { it.ip == ip }
                val resolver = builtin ?: Resolver(provider, ip, custom = true)
                fun opt(key: String): Long? =
                    if (o.isNull(key)) null else o.getLong(key)
                val status = ResolverStatus.valueOf(o.getString("status"))
                val answered = status != ResolverStatus.NO_ANSWER
                val correct = status == ResolverStatus.WORKING || status == ResolverStatus.SLOW
                out.add(
                    ScanResult(
                        resolver = resolver,
                        udpMs = opt("udpMs"),
                        tcpMs = opt("tcpMs"),
                        dotMs = opt("dotMs"),
                        dohMs = opt("dohMs"),
                        answered = answered,
                        correct = correct,
                    ),
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
