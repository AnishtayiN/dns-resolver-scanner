package com.anishtayin.dnsscanner.dns

/**
 * یک سرور عمومی DNS. فقط سرورهای عمومی شناخته‌شده در فهرست داخلی هستند؛
 * هیچ IP ساختگی‌ای اضافه نشده است.
 */
data class Resolver(
    val provider: String,
    val ip: String,
    val dotHost: String? = null,
    val dohUrl: String? = null,
    val custom: Boolean = false,
)

/** فهرست سرورهای عمومی شناخته‌شده. هر IP جداگانه آزمایش می‌شود. */
val BUILTIN_RESOLVERS: List<Resolver> = listOf(
    Resolver("Cloudflare", "1.1.1.1", "cloudflare-dns.com", "https://cloudflare-dns.com/dns-query"),
    Resolver("Cloudflare", "1.0.0.1", "cloudflare-dns.com", "https://cloudflare-dns.com/dns-query"),
    Resolver("Google", "8.8.8.8", "dns.google", "https://dns.google/dns-query"),
    Resolver("Google", "8.8.4.4", "dns.google", "https://dns.google/dns-query"),
    Resolver("Quad9", "9.9.9.9", "dns.quad9.net", "https://dns.quad9.net/dns-query"),
    Resolver("Quad9", "149.112.112.112", "dns.quad9.net", "https://dns.quad9.net/dns-query"),
    Resolver("OpenDNS", "208.67.222.222", "dns.opendns.com", "https://doh.opendns.com/dns-query"),
    Resolver("OpenDNS", "208.67.220.220", "dns.opendns.com", "https://doh.opendns.com/dns-query"),
    Resolver("AdGuard", "94.140.14.14", "dns.adguard-dns.com", "https://dns.adguard-dns.com/dns-query"),
    Resolver("AdGuard", "94.140.15.15", "dns.adguard-dns.com", "https://dns.adguard-dns.com/dns-query"),
    Resolver("ControlD", "76.76.2.0", "dns.controld.com", "https://dns.controld.com/dns-query"),
    Resolver("ControlD", "76.76.10.0", "dns.controld.com", "https://dns.controld.com/dns-query"),
)

/** اعتبارسنجی یک IPv4 که کاربر وارد می‌کند؛ در صورت نامعتبر بودن null. */
fun parseCustomIp(input: String): String? {
    val trimmed = input.trim()
    val parts = trimmed.split(".")
    if (parts.size != 4) return null
    for (part in parts) {
        if (part.isEmpty() || part.length > 3) return null
        if (!part.all { it.isDigit() }) return null
        val v = part.toIntOrNull() ?: return null
        if (v < 0 || v > 255) return null
    }
    if (trimmed == "0.0.0.0") return null
    return trimmed
}
