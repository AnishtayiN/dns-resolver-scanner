package com.anishtayin.dnsscanner.dns

/**
 * خلاصه‌ی صادقانه‌ی نتایج یک اسکن: فقط از تأخیرهای واقعاً اندازه‌گیری‌شده
 * حساب می‌شود؛ سرور بدون پاسخ در میانگین و سریع‌ترین جایی ندارد.
 */
data class ResultsSummary(
    val total: Int,
    val working: Int,
    val fastest: ScanResult?,
    val averageMs: Long?,
)

/** خلاصه‌ی نتایج اسکن. */
fun summarizeResults(results: List<ScanResult>): ResultsSummary {
    val measured = results.filter { it.correct && it.bestMs != null }
    val fastest = measured.minByOrNull { it.bestMs!! }
    val averageMs = if (measured.isEmpty()) {
        null
    } else {
        measured.sumOf { it.bestMs!! }.toDouble().div(measured.size).toLong()
    }
    return ResultsSummary(
        total = results.size,
        working = results.count { it.status == ResolverStatus.WORKING || it.status == ResolverStatus.SLOW },
        fastest = fastest,
        averageMs = averageMs,
    )
}

/**
 * کسر پر شدن میله‌ی تأخیر نسبت به سریع‌ترین سرور: سریع‌ترین سرور ۱٫۰ است و
 * هرچه سرور کندتر باشد میله کوتاه‌تر می‌شود. سرور بدون تأخیر ۰٫۰ می‌گیرد.
 */
fun latencyBarFraction(bestMs: Long?, fastestMs: Long?): Float {
    if (bestMs == null || fastestMs == null || fastestMs <= 0 || bestMs <= 0) return 0f
    return (fastestMs.toFloat() / bestMs.toFloat()).coerceIn(0f, 1f)
}

/** سریع‌ترین تأخیر اندازه‌گیری‌شده بین نتایج، یا null اگر هیچ‌کدام جواب نداده‌اند. */
fun fastestMeasuredMs(results: List<ScanResult>): Long? =
    results.mapNotNull { it.bestMs }.minOrNull()
