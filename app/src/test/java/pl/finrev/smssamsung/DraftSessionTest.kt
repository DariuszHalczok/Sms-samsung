package pl.finrev.smssamsung

import org.junit.Assert.*
import org.junit.Test

class DraftSessionTest {
    private fun person(i: Int, name: String = "Test $i") = Recipient("+120255501${i.toString().padStart(2, '0')}", name)
    @Test fun removingSelectedRecipientNeverAdvancesToAnotherPersonOrDeletesDraft() {
        val session = DraftSession()
        session.addPicked(person(1)); session.addPicked(person(2)); session.body = "Private draft"
        session.removeSelected()
        assertEquals(listOf(person(1).phone), session.batch.all().map { it.phone })
        assertNull(session.selected()); assertEquals("Private draft", session.body)
        session.removeSelected(); assertEquals(1, session.batch.all().size)
    }
    @Test fun repickingSameNumberUpdatesOnlyItsNameAndSelection() {
        val session = DraftSession()
        session.addPicked(person(1)); session.addPicked(person(2)); session.addPicked(person(1, "Updated Test"))
        assertEquals(2, session.batch.all().size)
        assertEquals("Updated Test", session.selected()?.name)
        assertEquals("Test 2", session.batch.all().single { it.phone == person(2).phone }.name)
    }
    @Test fun overLimitAndInvalidEditPreserveSelectionAndEntireList() {
        val session = DraftSession()
        session.replaceRecipients((1..30).map { person(it) }); session.select(person(5).phone)
        assertThrows(IllegalArgumentException::class.java) { session.addPicked(person(31)) }
        assertThrows(IllegalArgumentException::class.java) { session.addPicked(person(5, "Invalid\nname")) }
        assertThrows(IllegalArgumentException::class.java) { session.select(person(31).phone) }
        assertEquals(30, session.batch.all().size); assertEquals("Test 5", session.selected()?.name)
    }
    @Test fun changingModeClearsBoundContentAndMaterialButKeepsExplicitlySelectedPerson() {
        val session = DraftSession()
        session.addPicked(person(1)); session.mode = CommunicationMode.REQUESTED
        session.body = "Requested draft {link}"; session.material = Material.IKZE
        session.publicProfile = "11111111-1111-4111-8111-111111111111"
        session.changeMode(CommunicationMode.PRIVATE)
        assertEquals("", session.body); assertNull(session.material); assertEquals("", session.publicProfile)
        assertEquals(person(1).phone, session.selected()?.phone)
        session.clear(); assertNull(session.selected()); assertTrue(session.batch.all().isEmpty()); assertNull(session.mode)
    }
    @Test fun replacingListNeverSilentlySelectsAnotherPerson() {
        val session = DraftSession()
        session.addPicked(person(1)); session.replaceRecipients(listOf(person(2)))
        assertNull(session.selected()); assertEquals(1, session.batch.all().size)
        session.select(person(2).phone); session.replaceRecipients(listOf(person(2), person(3)))
        assertEquals(person(2).phone, session.selected()?.phone)
    }
    @Test fun preparedRepliesAreLimitedToRequestedMaterialsAndStillRequirePreviewConsent() {
        for (reply in RequestedReply.entries) {
            assertThrows(IllegalArgumentException::class.java) { reply.forDraft(CommunicationMode.PRIVATE, Material.IKZE) }
            assertThrows(IllegalArgumentException::class.java) { reply.forDraft(CommunicationMode.MARKETING, Material.IKZE) }
            assertThrows(IllegalArgumentException::class.java) { reply.forDraft(CommunicationMode.REQUESTED, null) }
            val template = reply.forDraft(CommunicationMode.REQUESTED, Material.IKZE)
            val link = Material.IKZE.url("11111111-1111-4111-8111-111111111111")
            assertTrue(Templates.render(template, person(1), link).contains(link))
            assertThrows(IllegalArgumentException::class.java) { DraftPolicy.requireAllowed(CommunicationMode.REQUESTED, false, Material.IKZE) }
        }
    }
}
