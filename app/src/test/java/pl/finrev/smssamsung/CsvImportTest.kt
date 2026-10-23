package pl.finrev.smssamsung

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class CsvImportTest {
    @Test fun handlesBomUtf8PolishAliasesAndCrLf() {
        val text = "\uFEFFimię;telefon\r\nTest Łukasz;+1 (202) 555-0101\r\n"
        val result = CsvImport.read(ByteArrayInputStream(text.toByteArray()))
        assertEquals("Test Łukasz", result.single().name)
        assertEquals("+12025550101", result.single().phone)
    }
    @Test fun handlesQuotedDelimitersEscapedQuotesAndMultilineExtraFields() {
        val rows = CsvImport.parse("name,phone,note\r\n\"Test, \"\"Example\"\"\",+12025550101,\"first\r\nsecond\"\r\n")
        assertEquals("Test, \"Example\"", rows.single().name)
        assertEquals("Test;Example", CsvImport.parse("name;phone\n\"Test;Example\";+12025550101").single().name)
    }
    @Test fun loopsOverPartialReads() {
        val bytes = "name;phone\nTest;+12025550101".toByteArray()
        val shortReads = object : InputStream() {
            var position = 0
            override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 255 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (position == bytes.size) return -1
                buffer[offset] = bytes[position++]
                return 1
            }
        }
        assertEquals(1, CsvImport.read(shortReads).size)
    }
    @Test fun refusesInvalidUtf8AndOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) { CsvImport.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }
        assertThrows(IllegalArgumentException::class.java) { CsvImport.read(ByteArrayInputStream(ByteArray(CsvImport.MAX_BYTES + 1))) }
    }
    @Test fun rejectsMalformedQuotesColumnsNumbersAndDuplicateHeaders() {
        listOf("name;phone\n\"Test;+12025550101", "name;phone\nTe\"st;+12025550101",
            "name;phone\n\"Test\"x;+12025550101", "name;phone\nTest;123", "name;phone\nTest;+12025550101;extra",
            "name;phone;telefon\nTest;+12025550101;+12025550102", "name;phone\n", "name\nTest").forEach {
            assertThrows(IllegalArgumentException::class.java) { CsvImport.parse(it) }
        }
    }
    @Test fun allowsOneTwentyThirtyAndCanonicalDuplicatesButRejectsThirtyOne() {
        for (size in listOf(1, 20, 30)) {
            val csv = "name;phone\n" + (1..size).joinToString("\n") { "Test;+120255501${it.toString().padStart(2, '0')}" }
            assertEquals(size, CsvImport.parse(csv).size)
        }
        assertEquals(1, CsvImport.parse("name;phone\nTest;+12025550101\nTest;0012025550101").size)
        val csv = "name;phone\n" + (1..31).joinToString("\n") { "Test;+120255501${it.toString().padStart(2, '0')}" }
        assertThrows(IllegalArgumentException::class.java) { CsvImport.parse(csv) }
    }
}
