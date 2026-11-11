package com.anishtayin.dnsscanner.dns

/**
 * نتیجه‌ی وارد کردن سرورها از یک فایل متنی.
 *
 * @param added آی‌پی‌های معتبر و تازه‌ای که اضافه شدند (به ترتیب دیده شدن در فایل).
 * @param duplicatesSkipped تعداد آی‌پی‌های معتبر که از قبل موجود بودند و رد شدند.
 * @param invalidCount تعداد توکن‌های نامعتبر که رد شدند.
 * @param invalidExamples حداکثر ۳ خط خامِ نمونه که توکن نامعتبر داشتند.
 * @param hadContent اگر فایل دست‌کم یک خط غیرخالی و غیرتوضیحی داشته باشد true است.
 */
data class TxtImportResult(
    val added: List<String>,
    val duplicatesSkipped: Int,
    val invalidCount: Int,
    val invalidExamples: List<String>,
    val hadContent: Boolean,
)

/**
 * تجزیه‌ی محتوای یک فایل متنی به آی‌پی‌های سرور.
 *
 * قالب پذیرفته‌شده:
 * - هر خط یک آی‌پی، یا چند آی‌پی با کاما یا فاصله از هم جدا شده؛
 * - خط‌های خالی نادیده گرفته می‌شوند؛
 * - خط‌هایی که با `#` شروع می‌شوند توضیح‌اند و نادیده گرفته می‌شوند؛
 * - توکن `#` به بعد در یک خط هم توضیح محسوب می‌شود؛
 * - فقط IPv4 سخت‌گیرانه (چهار اکتت ۰ تا ۲۵۵) پذیرفته می‌شود؛
 * - آی‌پی‌های تکراری — نسبت به [existingIps] و نسبت به خود فایل — رد می‌شوند.
 */
fun parseTxtImport(content: String, existingIps: Set<String>): TxtImportResult {
    val added = mutableListOf<String>()
    val seenInFile = mutableSetOf<String>()
    var duplicatesSkipped = 0
    var invalidCount = 0
    val invalidExamples = mutableListOf<String>()
    var hadContent = false

    for (rawLine in content.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        if (line.startsWith("#")) continue
        hadContent = true

        // کاما یا هر فاصله‌ای (فاصله، تب) جداکننده است.
        val tokens = line.split(',', ' ', '\t').filter { it.isNotEmpty() }
        var lineHasInvalid = false
        for (rawToken in tokens) {
            // «#» شروع توضیح است، حتی اگر به آی‌پی چسبیده باشد.
            val hasComment = '#' in rawToken
            val token = rawToken.substringBefore('#')
            if (token.isNotEmpty()) {
                val ip = parseCustomIp(token)
                if (ip == null) {
                    invalidCount++
                    lineHasInvalid = true
                } else if (ip in existingIps || ip in seenInFile) {
                    duplicatesSkipped++
                } else {
                    seenInFile.add(ip)
                    added.add(ip)
                }
            }
            if (hasComment) break // بقیه‌ی خط توضیح است
        }
        if (lineHasInvalid && invalidExamples.size < 3) {
            val example = line.take(60)
            if (example !in invalidExamples) invalidExamples.add(example)
        }
    }

    return TxtImportResult(
        added = added,
        duplicatesSkipped = duplicatesSkipped,
        invalidCount = invalidCount,
        invalidExamples = invalidExamples,
        hadContent = hadContent,
    )
}
