package com.anishtayin.dnsscanner.dns

/** وضعیت یک سرور پس از آزمایش واقعی. */
enum class ResolverStatus {
    WORKING,      // پاسخ درست دارد
    SLOW,         // پاسخ درست دارد ولی بهترین تأخیر بالای ۳۰۰ میلی‌ثانیه است
    NO_ANSWER,    // هیچ پروتکلی در مهلت تعیین‌شده جواب نداد
    WRONG_ANSWER, // جواب آمد ولی نادرست بود
}

/** نتیجه‌ی آزمایش یک سرور با همه‌ی پروتکل‌ها (میلی‌ثانیه؛ null یعنی بدون جواب). */
data class ScanResult(
    val resolver: Resolver,
    val udpMs: Long? = null,
    val tcpMs: Long? = null,
    val dotMs: Long? = null,
    val dohMs: Long? = null,
    val answered: Boolean = false,
    val correct: Boolean = false,
) {
    /** بهترین تأخیر بین پروتکل‌هایی که جواب درست داده‌اند. */
    val bestMs: Long?
        get() = listOfNotNull(udpMs, tcpMs, dotMs, dohMs).minOrNull()

    val status: ResolverStatus
        get() = when {
            !answered -> ResolverStatus.NO_ANSWER
            !correct -> ResolverStatus.WRONG_ANSWER
            (bestMs ?: Long.MAX_VALUE) > 300 -> ResolverStatus.SLOW
            else -> ResolverStatus.WORKING
        }

    /** پروتکلی که بهترین تأخیر را داده است. */
    val bestProtocol: String?
        get() {
            val candidates = listOf(
                "UDP" to udpMs, "TCP" to tcpMs, "DoT" to dotMs, "DoH" to dohMs,
            ).filter { it.second != null }
            return candidates.minByOrNull { it.second!! }?.first
        }
}

/** رتبه‌بندی: اول بر اساس بهترین تأخیر، سرورهای بدون جواب/نادرست آخر. */
fun rankResults(results: List<ScanResult>): List<ScanResult> =
    results.sortedWith(
        compareBy<ScanResult> { it.status == ResolverStatus.NO_ANSWER || it.status == ResolverStatus.WRONG_ANSWER }
            .thenBy { it.bestMs ?: Long.MAX_VALUE },
    )

/** خروجی متنی ساده از سرورهای سالم (برای اشتراک‌گذاری). */
fun resultsToText(results: List<ScanResult>): String {
    val sb = StringBuilder()
    sb.appendLine("DNS Scanner — working resolvers")
    for (r in rankResults(results).filter { it.correct }) {
        sb.appendLine("${r.resolver.provider} — ${r.resolver.ip} — ${r.bestMs} ms (${r.bestProtocol})")
    }
    return sb.toString()
}

/**
 * خروجی فایل ریزالورها با فرمت MasterDnsVPN: هر خط فقط یک IP ساده،
 * مرتب‌شده بر اساس تأخیر (سریع‌ترین اول). فقط سرورهایی که جواب درست
 * داده‌اند (سالم و کند) می‌آیند. خط‌های `#` در آن فرمت توضیح‌اند و
 * خوانده نمی‌شوند، پس سربرگ فقط توضیح است.
 */
fun resultsToMasterDnsResolvers(results: List<ScanResult>): String {
    val sb = StringBuilder()
    sb.appendLine("# DNS Scanner — working resolvers")
    sb.appendLine("# Save this file as client_resolvers.txt for the MasterDnsVPN client")
    for (r in rankResults(results).filter { it.correct }.distinctBy { it.resolver.ip }) {
        sb.appendLine(r.resolver.ip)
    }
    return sb.toString()
}

/** خروجی JSON از همه‌ی نتایج (ساخته‌شده با دست، بدون وابستگی). */
fun resultsToJson(results: List<ScanResult>, scannedAtEpochMs: Long): String {
    fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
    val sb = StringBuilder()
    sb.append("{\"scannedAt\":").append(scannedAtEpochMs).append(",\"results\":[")
    rankResults(results).forEachIndexed { index, r ->
        if (index > 0) sb.append(",")
        sb.append("{\"provider\":\"").append(esc(r.resolver.provider)).append("\"")
        sb.append(",\"ip\":\"").append(esc(r.resolver.ip)).append("\"")
        sb.append(",\"status\":\"").append(r.status.name).append("\"")
        sb.append(",\"bestMs\":").append(r.bestMs?.toString() ?: "null")
        sb.append(",\"udpMs\":").append(r.udpMs?.toString() ?: "null")
        sb.append(",\"tcpMs\":").append(r.tcpMs?.toString() ?: "null")
        sb.append(",\"dotMs\":").append(r.dotMs?.toString() ?: "null")
        sb.append(",\"dohMs\":").append(r.dohMs?.toString() ?: "null")
        sb.append("}")
    }
    sb.append("]}")
    return sb.toString()
}
