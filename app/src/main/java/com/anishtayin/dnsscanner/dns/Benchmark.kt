package com.anishtayin.dnsscanner.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * اجرای اسکن روی همه‌ی سرورها؛ هر سرور جداگانه آزمایش می‌شود و نتیجه
 * بر اساس بهترین تأخیر مرتب می‌شود. اسکن خارج از رشته‌ی اصلی انجام می‌شود.
 */
object Benchmark {

    private const val MAX_PARALLEL = 4

    /** آزمایش یک سرور با همه‌ی پروتکل‌های پشتیبانی‌شده. */
    suspend fun testResolver(resolver: Resolver): ScanResult = withContext(Dispatchers.IO) {
        val udp = DnsQueryEngine.queryUdp(resolver)
        // TCP فقط وقتی UDP جواب نداد
        val tcp = if (!udp.answered) DnsQueryEngine.queryTcp(resolver) else null
        val dot = if (resolver.dotHost != null) DnsQueryEngine.queryDot(resolver) else null
        val doh = if (resolver.dohUrl != null) DnsQueryEngine.queryDoh(resolver) else null
        val answered = udp.answered || tcp?.answered == true || dot?.answered == true || doh?.answered == true
        val correct = udp.correct || tcp?.correct == true || dot?.correct == true || doh?.correct == true
        ScanResult(
            resolver = resolver,
            udpMs = if (udp.correct) udp.latencyMs else null,
            tcpMs = if (tcp?.correct == true) tcp.latencyMs else null,
            dotMs = if (dot?.correct == true) dot.latencyMs else null,
            dohMs = if (doh?.correct == true) doh.latencyMs else null,
            answered = answered,
            correct = correct,
        )
    }

    /**
     * اسکن همه‌ی سرورها با حداکثر ۴ کار موازی؛ پس از پایان هر سرور،
     * پیشرفت (تعداد تمام‌شده / کل) گزارش می‌شود.
     */
    suspend fun runBenchmark(
        resolvers: List<Resolver>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<ScanResult> = coroutineScope {
        val done = AtomicInteger(0)
        val deferred = resolvers.map { resolver ->
            async(Dispatchers.IO.limitedParallelism(MAX_PARALLEL)) {
                val result = testResolver(resolver)
                onProgress(done.incrementAndGet(), resolvers.size)
                result
            }
        }
        rankResults(deferred.map { it.await() })
    }
}
