package com.anishtayin.dnsscanner.dns

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Base64
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/** نتیجه‌ی یک پرس‌وجوی منفرد. */
data class QueryOutcome(
    val latencyMs: Long?,
    val answered: Boolean,
    val correct: Boolean,
)

/**
 * موتور پرس‌وجوی DNS واقعی: UDP پورت ۵۳، TCP پورت ۵۳ (fallback)،
 * DoT پورت ۸۵۳ و DoH روی HTTPS. بدون هیچ کتابخانه‌ی DNS خارجی.
 */
object DnsQueryEngine {

    const val UDP_TIMEOUT_MS = 3000
    const val TCP_TIMEOUT_MS = 3000
    const val DOT_TIMEOUT_MS = 5000
    const val DOH_TIMEOUT_MS = 5000

    private fun newId(): Int = Random.nextInt(1, 65535)

    /** UDP پورت ۵۳. */
    fun queryUdp(resolver: Resolver, timeoutMs: Int = UDP_TIMEOUT_MS): QueryOutcome {
        val id = newId()
        val query = DnsPacket.buildQuery(id)
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(resolver.ip)
            val start = System.nanoTime()
            socket.send(DatagramPacket(query, query.size, address, 53))
            val buf = ByteArray(2048)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            val latency = (System.nanoTime() - start) / 1_000_000
            val parsed = DnsPacket.parseResponse(packet.data.copyOf(packet.length), id)
            val correct = DnsPacket.isCorrectAnswer(parsed)
            return QueryOutcome(latency, answered = true, correct = correct)
        } catch (_: Exception) {
            return QueryOutcome(null, answered = false, correct = false)
        } finally {
            socket?.close()
        }
    }

    /** TCP پورت ۵۳ با پیشوند ۲ بایتی طول (RFC 1035). */
    fun queryTcp(resolver: Resolver, timeoutMs: Int = TCP_TIMEOUT_MS): QueryOutcome {
        val id = newId()
        val query = DnsPacket.buildQuery(id)
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(resolver.ip, 53), timeoutMs)
            val out = DataOutputStream(socket.getOutputStream())
            out.writeShort(query.size)
            out.write(query)
            out.flush()
            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            if (length <= 0 || length > 4096) return QueryOutcome(null, answered = true, correct = false)
            val response = ByteArray(length)
            input.readFully(response)
            val latency = (System.nanoTime() - start) / 1_000_000
            val parsed = DnsPacket.parseResponse(response, id)
            val correct = DnsPacket.isCorrectAnswer(parsed)
            return QueryOutcome(latency, answered = true, correct = correct)
        } catch (_: Exception) {
            return QueryOutcome(null, answered = false, correct = false)
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }

    /** DoT پورت ۸۵۳ روی TLS با SNI نام میزبان ارائه‌دهنده. */
    fun queryDot(resolver: Resolver, timeoutMs: Int = DOT_TIMEOUT_MS): QueryOutcome {
        val host = resolver.dotHost ?: return QueryOutcome(null, answered = false, correct = false)
        val id = newId()
        val query = DnsPacket.buildQuery(id)
        var socket: Socket? = null
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket(resolver.ip, 853)
            socket.soTimeout = timeoutMs
            val params = SSLParameters()
            params.serverNames = listOf(SNIHostName(host))
            (socket as javax.net.ssl.SSLSocket).sslParameters = params
            val start = System.nanoTime()
            socket.startHandshake()
            val out = DataOutputStream(socket.getOutputStream())
            out.writeShort(query.size)
            out.write(query)
            out.flush()
            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            if (length <= 0 || length > 4096) return QueryOutcome(null, answered = true, correct = false)
            val response = ByteArray(length)
            input.readFully(response)
            val latency = (System.nanoTime() - start) / 1_000_000
            val parsed = DnsPacket.parseResponse(response, id)
            val correct = DnsPacket.isCorrectAnswer(parsed)
            return QueryOutcome(latency, answered = true, correct = correct)
        } catch (_: Exception) {
            return QueryOutcome(null, answered = false, correct = false)
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }

    /** DoH روی HTTPS (RFC 8484) با همان بسته‌ی باینری DNS. */
    fun queryDoh(resolver: Resolver, timeoutMs: Int = DOH_TIMEOUT_MS): QueryOutcome {
        val dohUrl = resolver.dohUrl ?: return QueryOutcome(null, answered = false, correct = false)
        val id = newId()
        val query = DnsPacket.buildQuery(id)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(query)
        var connection: java.net.HttpURLConnection? = null
        try {
            val start = System.nanoTime()
            connection = URL("$dohUrl?dns=$encoded").openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Accept", "application/dns-message")
            connection.connect()
            if (connection.responseCode != 200) {
                return QueryOutcome(null, answered = true, correct = false)
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input -> input.copyTo(bytes) }
            val latency = (System.nanoTime() - start) / 1_000_000
            val parsed = DnsPacket.parseResponse(bytes.toByteArray(), id)
            val correct = DnsPacket.isCorrectAnswer(parsed)
            return QueryOutcome(latency, answered = true, correct = correct)
        } catch (_: Exception) {
            return QueryOutcome(null, answered = false, correct = false)
        } finally {
            connection?.disconnect()
        }
    }
}
