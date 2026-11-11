package com.anishtayin.dnsscanner.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultSummaryTest {

    private fun result(ip: String, bestMs: Long?, answered: Boolean = true, correct: Boolean = true): ScanResult {
        val udp = if (correct) bestMs else null
        return ScanResult(
            resolver = Resolver("Test", ip),
            udpMs = udp,
            answered = answered,
            correct = correct,
        )
    }

    @Test
    fun `summary finds fastest average and working count`() {
        val results = listOf(
            result("1.1.1.1", 40),
            result("8.8.8.8", 80),
            result("9.9.9.9", null, answered = false, correct = false),
        )
        val summary = summarizeResults(results)
        assertEquals(3, summary.total)
        assertEquals(2, summary.working)
        assertEquals("1.1.1.1", summary.fastest!!.resolver.ip)
        assertEquals(60L, summary.averageMs)
    }

    @Test
    fun `summary of empty results has no fastest or average`() {
        val summary = summarizeResults(emptyList())
        assertEquals(0, summary.total)
        assertEquals(0, summary.working)
        assertNull(summary.fastest)
        assertNull(summary.averageMs)
    }

    @Test
    fun `slow resolvers count as working but never fastest if slower`() {
        // bestMs بالای ۳۰۰ یعنی وضعیت SLOW ولی همچنان correct است.
        val results = listOf(result("1.1.1.1", 500), result("8.8.8.8", 60))
        val summary = summarizeResults(results)
        assertEquals(2, summary.working)
        assertEquals("8.8.8.8", summary.fastest!!.resolver.ip)
        assertEquals(280L, summary.averageMs)
    }

    @Test
    fun `latency bar fraction is fastest over best`() {
        assertEquals(1.0f, latencyBarFraction(40, 40))
        assertEquals(0.5f, latencyBarFraction(80, 40))
        assertEquals(0.25f, latencyBarFraction(160, 40))
    }

    @Test
    fun `latency bar fraction clamps and handles nulls`() {
        assertEquals(0f, latencyBarFraction(null, 40))
        assertEquals(0f, latencyBarFraction(40, null))
        assertEquals(0f, latencyBarFraction(40, 0))
        // سریع‌ترین نمی‌تواند کندتر از بهترین باشد؛ اگر رخ داد، سقف ۱٫۰ است.
        assertEquals(1.0f, latencyBarFraction(30, 40))
    }

    @Test
    fun `fastest measured ignores unanswered resolvers`() {
        val results = listOf(
            result("1.1.1.1", null, answered = false, correct = false),
            result("8.8.8.8", 90),
        )
        assertEquals(90L, fastestMeasuredMs(results))
        assertNull(fastestMeasuredMs(listOf(result("1.1.1.1", null, answered = false, correct = false))))
    }
}
