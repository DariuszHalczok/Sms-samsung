package pl.finrev.smssamsung

import org.junit.Assert.*
import org.junit.Test

class DraftRulesTest {
    private fun recipient(i: Int = 1, name: String = "Test Example", link: String = "") =
        Recipient("+120255501${i.toString().padStart(2, '0')}", name, link)

    @Test fun phoneNormalizationAndInternationalNumbers() {
        listOf("600 000 000", "48 600 000 000", "0048 600 000 000", "+48 (600) 000-000")
            .forEach { assertEquals("+48600000000", Phones.normalize(it)) }
        assertEquals("+12025550101", Phones.normalize("+1 (202) 555-0101"))
        assertEquals("+442079460018", Phones.normalize("0044 20 7946 0018"))
    }

    @Test fun rejectsMalformedAndImpossiblePhones() {
        listOf("123", "+48000000000", "++12025550101", "+12025550101 ext 2", "tel:+12025550101",
            "+12025550101;123", "６００００００００", "+0123456789", "+12025550101123456").forEach {
            assertThrows(IllegalArgumentException::class.java) { Phones.normalize(it) }
        }
    }

    @Test fun batchLimitsAndAtomicReplacement() {
        val batch = RecipientBatch()
        for (size in listOf(1, 20, 30)) {
            batch.replace((1..size).map { recipient(it) })
            assertEquals(size, batch.all().size)
        }
        assertThrows(IllegalArgumentException::class.java) { batch.replace((1..31).map { recipient(it) }) }
        assertEquals(30, batch.all().size)
        assertThrows(IllegalArgumentException::class.java) { batch.replace(listOf(Recipient("123", "Test"))) }
        assertEquals(30, batch.all().size)
        batch.clear()
        assertTrue(batch.all().isEmpty())
    }

    @Test fun deduplicatesCanonicalPhonesAndRejectsConflictingLinks() {
        val batch = RecipientBatch()
        batch.replace(listOf(recipient(), Recipient("0012025550101", "Test Example")))
        assertEquals(1, batch.all().size)
        assertThrows(IllegalArgumentException::class.java) {
            batch.replace(listOf(recipient(link = "https://doradca.pro/track/first"), recipient(link = "https://doradca.pro/track/second")))
        }
        assertEquals(1, batch.all().size)
    }

    @Test fun personalizesInOnePassAndRequiresEveryReferencedValue() {
        assertEquals("Cześć Test Example https://doradca.pro/track/opaque-code",
            Templates.render("Cześć {imię} {nazwisko} {link}", recipient(link = "https://doradca.pro/track/opaque-code")))
        listOf("Zapraszam {link}", "Witaj {unknown}", "Witaj {imie", "", "x".repeat(4001)).forEach {
            assertThrows(IllegalArgumentException::class.java) { Templates.render(it, recipient()) }
        }
        assertThrows(IllegalArgumentException::class.java) { Templates.render("{imie}", recipient(name = "{link}")) }
        assertThrows(IllegalArgumentException::class.java) { Templates.render("{imie}", recipient(name = "")) }
        assertThrows(IllegalArgumentException::class.java) { Templates.render("{imie}".repeat(1000), recipient()) }
    }

    @Test fun httpsPolicyRejectsForeignHostsPortsCredentialsQueryAndEncoding() {
        assertEquals("doradca.pro", DoradcaUrls.validate("https://doradca.pro/track/opaque-123").host)
        listOf("http://doradca.pro/x", "https://doradca.pro.evil.test/x", "https://evil.test/x",
            "https://doradca.pro@evil.test/x", "https://user:secret@doradca.pro/x", "https://doradca.pro:444/x",
            "https://doradca.pro/x?phone=example", "https://doradca.pro/x#token", "https://doradca.pro/%2e%2e/x",
            "https://doradca.pro/../x", "https://doradca.pro./x", "https://127.0.0.1/x", "https://doradca.pro/x\\y").forEach {
            assertThrows(IllegalArgumentException::class.java) { DoradcaUrls.validate(it) }
        }
    }

    @Test fun estimatesGsmAndUnicodeSegmentsIncludingExtensionsAndEmoji() {
        assertEquals(SmsSize("GSM-7", 160, 1), SmsSizing.calculate("a".repeat(160)))
        assertEquals(2, SmsSizing.calculate("a".repeat(161)).segments)
        assertEquals(SmsSize("GSM-7", 162, 2), SmsSizing.calculate("^".repeat(81)))
        assertEquals(SmsSize("Unicode", 70, 1), SmsSizing.calculate("ą".repeat(70)))
        assertEquals(2, SmsSizing.calculate("ą".repeat(71)).segments)
        assertEquals(72, SmsSizing.calculate("😀".repeat(36)).units)
    }
}
