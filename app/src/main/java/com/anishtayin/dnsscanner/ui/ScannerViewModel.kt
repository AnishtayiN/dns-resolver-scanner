package com.anishtayin.dnsscanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anishtayin.dnsscanner.data.HistoryStore
import com.anishtayin.dnsscanner.dns.BUILTIN_RESOLVERS
import com.anishtayin.dnsscanner.dns.Benchmark
import com.anishtayin.dnsscanner.dns.Resolver
import com.anishtayin.dnsscanner.dns.ResolverStatus
import com.anishtayin.dnsscanner.dns.ResultsSummary
import com.anishtayin.dnsscanner.dns.ScanResult
import com.anishtayin.dnsscanner.dns.TxtImportResult
import com.anishtayin.dnsscanner.dns.parseCustomIp
import com.anishtayin.dnsscanner.dns.parseTxtImport
import com.anishtayin.dnsscanner.dns.resultsToJson
import com.anishtayin.dnsscanner.dns.resultsToMasterDnsResolvers
import com.anishtayin.dnsscanner.dns.resultsToText
import com.anishtayin.dnsscanner.dns.summarizeResults
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** نتیجه‌ی یک بار تلاش برای وارد کردن فایل TXT. */
sealed interface TxtImportOutcome {
    /** فایل خالی بود یا فقط خط خالی/توضیح داشت. */
    data object EmptyFile : TxtImportOutcome

    /** فایل باز یا خوانده نشد. */
    data object UnreadableFile : TxtImportOutcome

    /** فایل خوانده و تجزیه شد؛ خلاصه‌ی صادقانه‌ی آن در [result] است. */
    data class Completed(val result: TxtImportResult) : TxtImportOutcome
}

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

    /** سرورهایی که از فایل TXT وارد شده‌اند (جدا از سرورهای دستی). */
    private val _importedIps = MutableStateFlow<List<String>>(emptyList())
    val importedIps: StateFlow<List<String>> = _importedIps.asStateFlow()

    private val _invalidIpAttempt = MutableStateFlow(false)
    val invalidIpAttempt: StateFlow<Boolean> = _invalidIpAttempt.asStateFlow()

    private val _importOutcome = MutableStateFlow<TxtImportOutcome?>(null)
    val importOutcome: StateFlow<TxtImportOutcome?> = _importOutcome.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            _customIps.value = store.loadCustomIps()
            _importedIps.value = store.loadImportedIps()
            val (json, time) = store.loadLastScan()
            _lastScanTime.value = time
            if (json != null) _results.value = parseResultsJson(json, _importedIps.value.toSet())
        }
    }

    private fun allResolvers(): List<Resolver> {
        val custom = _customIps.value.map { Resolver("Custom", it, custom = true) }
        val imported = _importedIps.value.map { Resolver("Custom", it, custom = true, imported = true) }
        return BUILTIN_RESOLVERS + custom + imported
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
        if (ip in _customIps.value || ip in _importedIps.value) return true
        val updated = _customIps.value + ip
        _customIps.value = updated
        viewModelScope.launch { store.saveCustomIps(updated) }
        return true
    }

    fun clearInvalidIpFlag() {
        _invalidIpAttempt.value = false
    }

    fun removeCustomIp(ip: String) {
        val updatedCustom = _customIps.value - ip
        _customIps.value = updatedCustom
        val updatedImported = _importedIps.value - ip
        _importedIps.value = updatedImported
        viewModelScope.launch {
            store.saveCustomIps(updatedCustom)
            store.saveImportedIps(updatedImported)
        }
        _results.value = _results.value.filterNot { it.resolver.ip == ip }
    }

    /**
     * وارد کردن سرورها از محتوای خوانده‌شده‌ی یک فایل TXT.
     * فایل خالی، یا فایلی که فقط توضیح دارد، [TxtImportOutcome.EmptyFile] می‌دهد؛
     * فایلی با صفر آی‌پی معتبر جدید هم با همان خلاصه‌ی صادقانه گزارش می‌شود.
     */
    fun importTxtContent(content: String) {
        if (content.isBlank()) {
            _importOutcome.value = TxtImportOutcome.EmptyFile
            return
        }
        val existing = BUILTIN_RESOLVERS.map { it.ip }.toSet() + _customIps.value + _importedIps.value
        val result = parseTxtImport(content, existing)
        if (!result.hadContent) {
            _importOutcome.value = TxtImportOutcome.EmptyFile
            return
        }
        if (result.added.isNotEmpty()) {
            val updated = _importedIps.value + result.added
            _importedIps.value = updated
            viewModelScope.launch { store.saveImportedIps(updated) }
        }
        _importOutcome.value = TxtImportOutcome.Completed(result)
    }

    /** وقتی خواندن فایل از انتخاب‌گر سیستم ناموفق بود. */
    fun reportImportUnreadable() {
        _importOutcome.value = TxtImportOutcome.UnreadableFile
    }

    fun clearImportOutcome() {
        _importOutcome.value = null
    }

    /** پاک کردن همه‌ی سرورهای واردشده از فایل (سرورهای دستی و داخلی می‌مانند). */
    fun clearImported() {
        val importedSet = _importedIps.value.toSet()
        _importedIps.value = emptyList()
        viewModelScope.launch { store.saveImportedIps(emptyList()) }
        _results.value = _results.value.filterNot { it.resolver.ip in importedSet }
    }

    fun exportText(): String = resultsToText(_results.value)

    fun exportJson(): String =
        resultsToJson(_results.value, _lastScanTime.value ?: System.currentTimeMillis())

    /** خروجی فایل ریزالورها با فرمت MasterDnsVPN (هر خط فقط یک IP ساده). */
    fun exportMasterDns(): String = resultsToMasterDnsResolvers(_results.value)

    fun workingCount(): Int = _results.value.count { it.status == ResolverStatus.WORKING || it.status == ResolverStatus.SLOW }

    /** خلاصه‌ی نتایج اسکن فعلی (سریع‌ترین، میانگین، تعداد سالم). */
    fun summary(): ResultsSummary = summarizeResults(_results.value)

    /** بازسازی نتایج از JSON ذخیره‌شده‌ی خودمان (فقط تاریخچه‌ی نمایشی). */
    private fun parseResultsJson(json: String, importedIps: Set<String>): List<ScanResult> {
        return try {
            val root = JSONObject(json)
            val arr: JSONArray = root.getJSONArray("results")
            val out = mutableListOf<ScanResult>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ip = o.getString("ip")
                val provider = o.getString("provider")
                val builtin = BUILTIN_RESOLVERS.find { it.ip == ip }
                val resolver = builtin ?: Resolver(provider, ip, custom = true, imported = ip in importedIps)
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
