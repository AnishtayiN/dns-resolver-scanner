package com.anishtayin.dnsscanner.dns

/**
 * ساخت و تجزیه‌ی بسته‌ی DNS (RFC 1035) — منطق خالص، بدون وابستگی به اندروید.
 * فقط برای پرس‌وجوی رکورد A به کار می‌رود.
 */
object DnsPacket {

    const val QUERY_DOMAIN = "example.com"
    const val TYPE_A = 1
    const val CLASS_IN = 1

    /** یک پرس‌وجوی استاندارد DNS با پرچم RD می‌سازد. */
    fun buildQuery(id: Int, domain: String = QUERY_DOMAIN): ByteArray {
        val labels = domain.split(".")
        // هدر ۱۲ بایت + برچسب‌ها + بایت پایانی QNAME + ۴ بایت (QTYPE + QCLASS)
        var size = 12 + 4 + 1
        for (label in labels) size += 1 + label.length
        val out = ByteArray(size)
        var p = 0
        fun putShort(v: Int) {
            out[p++] = ((v ushr 8) and 0xFF).toByte()
            out[p++] = (v and 0xFF).toByte()
        }
        putShort(id and 0xFFFF)
        putShort(0x0100) // RD = 1
        putShort(1) // QDCOUNT
        putShort(0) // ANCOUNT
        putShort(0) // NSCOUNT
        putShort(0) // ARCOUNT
        for (label in labels) {
            out[p++] = label.length.toByte()
            for (c in label) out[p++] = c.code.toByte()
        }
        out[p++] = 0 // پایان QNAME
        putShort(TYPE_A)
        putShort(CLASS_IN)
        return out
    }

    data class ParsedAnswer(val id: Int, val rcode: Int, val aRecords: List<String>)

    /** پاسخ را تجزیه می‌کند؛ اگر ساختار خراب باشد null برمی‌گرداند. */
    fun parseResponse(data: ByteArray, expectedId: Int): ParsedAnswer? {
        if (data.size < 12) return null
        fun u16(off: Int): Int = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
        val id = u16(0)
        if (id != (expectedId and 0xFFFF)) return null
        val flags = u16(2)
        val qdCount = u16(4)
        val anCount = u16(6)
        val rcode = flags and 0x0F
        var p = 12
        // رد کردن بخش سؤال
        repeat(qdCount) {
            p = skipName(data, p) ?: return null
            if (p + 4 > data.size) return null
            p += 4
        }
        val records = mutableListOf<String>()
        repeat(anCount) {
            p = skipName(data, p) ?: return null
            if (p + 10 > data.size) return null
            val type = u16(p)
            val rdLength = u16(p + 8)
            p += 10
            if (p + rdLength > data.size) return null
            if (type == TYPE_A && rdLength == 4) {
                val ip = (0 until 4).joinToString(".") { (data[p + it].toInt() and 0xFF).toString() }
                records.add(ip)
            }
            p += rdLength
        }
        return ParsedAnswer(id, rcode, records)
    }

    /** رد کردن یک نام دامنه (برچسب‌ها یا اشاره‌گر فشرده 0xC0). */
    private fun skipName(data: ByteArray, start: Int): Int? {
        var p = start
        while (true) {
            if (p >= data.size) return null
            val len = data[p].toInt() and 0xFF
            if (len == 0) return p + 1
            if (len and 0xC0 == 0xC0) {
                // اشاره‌گر فشرده: دو بایت
                if (p + 1 >= data.size) return null
                return p + 2
            }
            p += 1 + len
        }
    }

    /**
     * آیا این IPv4 یک آدرس عمومی قابل‌قبول است؟
     * 0.0.0.0، loopback، خصوصی و رزرو شده رد می‌شوند.
     */
    fun isPlausiblePublicIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val o = IntArray(4)
        for (i in 0 until 4) {
            val v = parts[i].toIntOrNull() ?: return false
            if (v < 0 || v > 255) return false
            o[i] = v
        }
        if (o[0] == 0) return false
        if (o[0] == 10) return false
        if (o[0] == 127) return false
        if (o[0] == 169 && o[1] == 254) return false
        if (o[0] == 172 && o[1] in 16..31) return false
        if (o[0] == 192 && o[1] == 168) return false
        if (o[0] == 100 && o[1] in 64..127) return false
        if (o[0] == 192 && o[1] == 0) return false
        if (o[0] >= 224) return false
        return true
    }

    /** پاسخ درست است اگر RCODE صفر باشد و دست‌کم یک رکورد A عمومی داشته باشد. */
    fun isCorrectAnswer(parsed: ParsedAnswer?): Boolean {
        if (parsed == null) return false
        if (parsed.rcode != 0) return false
        return parsed.aRecords.any { isPlausiblePublicIpv4(it) }
    }
}
