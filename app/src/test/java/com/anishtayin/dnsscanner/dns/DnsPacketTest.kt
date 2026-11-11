package com.anishtayin.dnsscanner.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {

    @Test
    fun `buildQuery produces a well-formed header`() {
        val query = DnsPacket.buildQuery(0x1234, "example.com")
        // هدر ۱۲ بایت + QNAME (۱۳ بایت) + QTYPE/QCLASS (۴ بایت) = ۲۹ بایت
        assertEquals(29, query.size)
        assertEquals(0x12, query[0].toInt() and 0xFF)
        assertEquals(0x34, query[1].toInt() and 0xFF)
        assertEquals(0x01, query[2].toInt() and 0xFF) // RD
        assertEquals(0x00, query[3].toInt() and 0xFF)
        assertEquals(1, query[5].toInt() and 0xFF) // QDCOUNT
    }

    @Test
    fun `parseResponse extracts an A record from a hand-built response`() {
        val id = 0x1234
        val query = DnsPacket.buildQuery(id, "example.com")
        // پاسخ دستی: هدر + سؤال (کپی از پرس‌وجو) + یک رکورد A
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C, // اشاره‌گر به نام سؤال
            0x00, 0x01, // TYPE A
            0x00, 0x01, // CLASS IN
            0x00, 0x00, 0x0E, 0x10, // TTL
            0x00, 0x04, // RDLENGTH
            93.toByte(), 184.toByte(), 216.toByte(), 34, // 93.184.216.34
        )
        val response = ByteArray(12 + (query.size - 12) + answer.size)
        response[0] = 0x12
        response[1] = 0x34
        response[2] = 0x81.toByte() // QR + RD
        response[3] = 0x80.toByte() // RA
        response[4] = 0x00
        response[5] = 0x01 // QDCOUNT
        response[6] = 0x00
        response[7] = 0x01 // ANCOUNT
        query.copyInto(response, 12, 12, query.size)
        answer.copyInto(response, 12 + (query.size - 12))
        val parsed = DnsPacket.parseResponse(response, id)
        assertNotNull(parsed)
        assertEquals(0, parsed!!.rcode)
        assertEquals(listOf("93.184.216.34"), parsed.aRecords)
        assertTrue(DnsPacket.isCorrectAnswer(parsed))
    }

    @Test
    fun `parseResponse rejects a mismatched transaction id`() {
        val response = ByteArray(12)
        response[0] = 0x00
        response[1] = 0x01
        assertNull(DnsPacket.parseResponse(response, 0x1234))
    }

    @Test
    fun `isPlausiblePublicIpv4 rejects private and special addresses`() {
        assertTrue(DnsPacket.isPlausiblePublicIpv4("93.184.216.34"))
        assertTrue(DnsPacket.isPlausiblePublicIpv4("1.1.1.1"))
        assertFalse(DnsPacket.isPlausiblePublicIpv4("0.0.0.0"))
        assertFalse(DnsPacket.isPlausiblePublicIpv4("127.0.0.1"))
        assertFalse(DnsPacket.isPlausiblePublicIpv4("10.0.0.5"))
        assertFalse(DnsPacket.isPlausiblePublicIpv4("192.168.1.1"))
        assertFalse(DnsPacket.isPlausiblePublicIpv4("172.16.0.1"))
        assertFalse(DnsPacket.isPlausiblePublicIpv4("169.254.1.1"))
        assertFalse(DnsPacket.isPlausiblePublicIpv4("not-an-ip"))
        assertFalse(DnsPacket.isPlausiblePublicIpv4("999.1.1.1"))
    }

    @Test
    fun `rankResults sorts by best latency and puts failures last`() {
        val fast = ScanResult(BUILTIN_RESOLVERS[0], udpMs = 25, answered = true, correct = true)
        val slow = ScanResult(BUILTIN_RESOLVERS[1], udpMs = 400, answered = true, correct = true)
        val dead = ScanResult(BUILTIN_RESOLVERS[2], answered = false, correct = false)
        val wrong = ScanResult(BUILTIN_RESOLVERS[3], udpMs = 10, answered = true, correct = false)
        val ranked = rankResults(listOf(slow, dead, wrong, fast))
        assertEquals("1.1.1.1", ranked[0].resolver.ip)
        assertEquals("1.0.0.1", ranked[1].resolver.ip)
        assertTrue(ranked[2].status == ResolverStatus.NO_ANSWER || ranked[2].status == ResolverStatus.WRONG_ANSWER)
        assertTrue(ranked[3].status == ResolverStatus.NO_ANSWER || ranked[3].status == ResolverStatus.WRONG_ANSWER)
    }

    @Test
    fun `status is SLOW above 300 ms and WORKING below`() {
        val slow = ScanResult(BUILTIN_RESOLVERS[0], udpMs = 301, answered = true, correct = true)
        val ok = ScanResult(BUILTIN_RESOLVERS[0], udpMs = 300, answered = true, correct = true)
        assertEquals(ResolverStatus.SLOW, slow.status)
        assertEquals(ResolverStatus.WORKING, ok.status)
    }

    @Test
    fun `parseCustomIp validates IPv4 input`() {
        assertEquals("1.1.1.1", parseCustomIp("1.1.1.1"))
        assertEquals("8.8.8.8", parseCustomIp("  8.8.8.8  "))
        assertNull(parseCustomIp("999.1.1.1"))
        assertNull(parseCustomIp("1.1.1"))
        assertNull(parseCustomIp("example.com"))
        assertNull(parseCustomIp("0.0.0.0"))
    }

    @Test
    fun `resultsToJson contains every resolver ip`() {
        val results = listOf(
            ScanResult(BUILTIN_RESOLVERS[0], udpMs = 25, answered = true, correct = true),
            ScanResult(BUILTIN_RESOLVERS[1], answered = false, correct = false),
        )
        val json = resultsToJson(results, 1234567890L)
        assertTrue(json.contains("1.1.1.1"))
        assertTrue(json.contains("1.0.0.1"))
        assertTrue(json.contains("WORKING"))
        assertTrue(json.contains("NO_ANSWER"))
    }
}
