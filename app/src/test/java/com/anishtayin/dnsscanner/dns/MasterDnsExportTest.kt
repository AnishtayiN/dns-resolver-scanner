package com.anishtayin.dnsscanner.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDnsExportTest {

    private val ipv4Line = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")

    private fun sampleResults() = listOf(
        ScanResult(BUILTIN_RESOLVERS[0], udpMs = 42, answered = true, correct = true), // 1.1.1.1
        ScanResult(BUILTIN_RESOLVERS[2], udpMs = 18, answered = true, correct = true), // 8.8.8.8
        ScanResult(BUILTIN_RESOLVERS[1], udpMs = 350, answered = true, correct = true), // 1.0.0.1 slow
        ScanResult(BUILTIN_RESOLVERS[3], answered = false, correct = false), // 8.8.4.4 no answer
        ScanResult(BUILTIN_RESOLVERS[4], udpMs = 60, answered = true, correct = false), // 9.9.9.9 wrong answer
    )

    @Test
    fun `only correct resolvers are included fastest first`() {
        val out = resultsToMasterDnsResolvers(sampleResults())
        val ips = out.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(listOf("8.8.8.8", "1.1.1.1", "1.0.0.1"), ips)
    }

    @Test
    fun `slow but correct resolvers are included`() {
        val out = resultsToMasterDnsResolvers(sampleResults())
        assertTrue(out.lines().contains("1.0.0.1"))
    }

    @Test
    fun `no answer and wrong answer resolvers are excluded`() {
        val out = resultsToMasterDnsResolvers(sampleResults())
        val ips = out.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(!ips.contains("8.8.4.4"))
        assertTrue(!ips.contains("9.9.9.9"))
    }

    @Test
    fun `every data line is a bare ipv4 address`() {
        val out = resultsToMasterDnsResolvers(sampleResults())
        val dataLines = out.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(dataLines.isNotEmpty())
        for (line in dataLines) {
            assertTrue("line is not a bare IPv4: $line", ipv4Line.matches(line))
            assertTrue("line has extra text: $line", !line.contains(" ") && !line.contains("—"))
        }
    }

    @Test
    fun `header lines are comments only`() {
        val out = resultsToMasterDnsResolvers(sampleResults())
        val header = out.lines().takeWhile { it.startsWith("#") }
        assertTrue(header.isNotEmpty())
        assertTrue(out.lines().filter { it.isNotBlank() }.all { it.startsWith("#") || ipv4Line.matches(it) })
    }

    @Test
    fun `empty results produce header only without crashing`() {
        val out = resultsToMasterDnsResolvers(emptyList())
        val dataLines = out.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(dataLines.isEmpty())
        assertTrue(out.lines().any { it.startsWith("#") })
    }

    @Test
    fun `duplicate ips appear only once`() {
        val dup = listOf(
            ScanResult(Resolver("Cloudflare", "1.1.1.1"), udpMs = 30, answered = true, correct = true),
            ScanResult(Resolver("Cloudflare", "1.1.1.1"), udpMs = 25, answered = true, correct = true),
            ScanResult(Resolver("Google", "8.8.8.8"), udpMs = 40, answered = true, correct = true),
        )
        val out = resultsToMasterDnsResolvers(dup)
        val ips = out.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), ips)
    }
}
