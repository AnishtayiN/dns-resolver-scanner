package com.anishtayin.dnsscanner.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtImportTest {

    private val builtin = setOf("1.1.1.1", "8.8.8.8")

    @Test
    fun `one ip per line is imported`() {
        val result = parseTxtImport("1.0.0.1\n9.9.9.9\n", builtin)
        assertEquals(listOf("1.0.0.1", "9.9.9.9"), result.added)
        assertEquals(0, result.duplicatesSkipped)
        assertEquals(0, result.invalidCount)
        assertTrue(result.hadContent)
    }

    @Test
    fun `blank lines and full-line comments are ignored`() {
        val result = parseTxtImport("\n# توضیح\n   \n# یک خط دیگر\n1.0.0.1\n", builtin)
        assertEquals(listOf("1.0.0.1"), result.added)
        assertEquals(0, result.invalidCount)
        assertTrue(result.hadContent)
    }

    @Test
    fun `comma and space separated ips on one line are imported`() {
        val result = parseTxtImport("1.0.0.1, 9.9.9.9 149.112.112.112\n", builtin)
        assertEquals(listOf("1.0.0.1", "9.9.9.9", "149.112.112.112"), result.added)
    }

    @Test
    fun `inline comment after an ip is ignored`() {
        val result = parseTxtImport("1.0.0.1 # خانه\n9.9.9.9#بدون فاصله\n", builtin)
        assertEquals(listOf("1.0.0.1", "9.9.9.9"), result.added)
        assertEquals(0, result.invalidCount)
    }

    @Test
    fun `duplicates against builtins and inside the file are skipped`() {
        val result = parseTxtImport("1.1.1.1\n1.0.0.1\n1.0.0.1\n8.8.8.8\n", builtin)
        assertEquals(listOf("1.0.0.1"), result.added)
        assertEquals(3, result.duplicatesSkipped)
    }

    @Test
    fun `invalid entries are counted with examples`() {
        val result = parseTxtImport("999.1.1.1\nhello\n1.2.3\n1.0.0.1\n", builtin)
        assertEquals(listOf("1.0.0.1"), result.added)
        assertEquals(3, result.invalidCount)
        assertEquals(3, result.invalidExamples.size)
        assertTrue(result.invalidExamples.contains("999.1.1.1"))
    }

    @Test
    fun `only first three invalid examples are kept`() {
        val content = (1..6).joinToString("\n") { "bad-$it" } + "\n"
        val result = parseTxtImport(content, builtin)
        assertEquals(6, result.invalidCount)
        assertEquals(3, result.invalidExamples.size)
    }

    @Test
    fun `empty file has no content`() {
        val result = parseTxtImport("", builtin)
        assertTrue(result.added.isEmpty())
        assertFalse(result.hadContent)
    }

    @Test
    fun `file with only comments has no content`() {
        val result = parseTxtImport("# فقط توضیح\n\n# یکی دیگر\n", builtin)
        assertTrue(result.added.isEmpty())
        assertFalse(result.hadContent)
    }

    @Test
    fun `file with zero valid ips reports invalids honestly`() {
        val result = parseTxtImport("hello\n999.1.1.1\n", builtin)
        assertTrue(result.added.isEmpty())
        assertEquals(2, result.invalidCount)
        assertTrue(result.hadContent)
    }

    @Test
    fun `whitespace is trimmed and strict ipv4 enforced`() {
        val result = parseTxtImport("  1.0.0.1  \n01.2.3.4\n256.1.1.1\n1.2.3.4.5\n", builtin)
        // «01.2.3.4» چهار اکتت معتبر دارد و پذیرفته می‌شود؛ بقیه رد می‌شوند.
        assertEquals(listOf("1.0.0.1", "01.2.3.4"), result.added)
        assertEquals(2, result.invalidCount)
    }
}
