package pl.finrev.smssamsung

import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class WarmMarketTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private fun recipient(i: Int = 1, link: String = "https://doradca.pro/c/" + "A".repeat(42) + "B") =
        Recipient("+120255501${i.toString().padStart(2, '0')}", "Test Example", link)
    private fun campaign(clients: List<Recipient> = listOf(recipient()), expiry: Instant = now.plusSeconds(3600)) =
        WarmCampaign(UUID.randomUUID().toString(), expiry, "Cześć {imie}, zapraszam: {link}", clients)

    @Test fun validatesOneTwentyThirtyContactsAndIndividualOpaqueLinks() {
        for (count in listOf(1, 20, 30)) {
            val clients = (1..count).map { recipient(it, "https://doradca.pro/c/" + it.toString().padStart(43, 'A')) }
            val result = campaign(clients)
            assertSame(result, WarmRules.validateCampaign(result, clients.map { Recipient(it.phone, it.name) }, now))
        }
    }
    @Test fun refusesExpiredAndExcessiveTtlCampaigns() {
        assertThrows(WarmFailure::class.java) { WarmRules.validateCampaign(campaign(expiry = now), listOf(recipient()), now) }
        assertThrows(WarmFailure::class.java) { WarmRules.validateCampaign(campaign(expiry = now.plusSeconds(8 * 86400)), listOf(recipient()), now) }
    }
    @Test fun rejectsMissingExtraDuplicatedAndChangedContacts() {
        val expected = listOf(recipient())
        listOf(campaign(emptyList()), campaign(listOf(recipient(2))), campaign(listOf(recipient(), recipient())),
            campaign(listOf(Recipient(expected[0].phone, "Other Test", expected[0].clickUrl)))).forEach {
            assertThrows(WarmFailure::class.java) { WarmRules.validateCampaign(it, expected, now) }
        }
    }
    @Test fun rejectsForeignMalformedSharedOrNonOpaqueLinks() {
        for (link in listOf("https://evil.test/c/" + "A".repeat(43), "https://doradca.pro/c/short",
            "https://doradca.pro/c/" + "A".repeat(43) + "?name=Test", "https://doradca.pro/other/" + "A".repeat(43))) {
            assertThrows(IllegalArgumentException::class.java) { WarmRules.validateCampaign(campaign(listOf(recipient(link = link))), listOf(recipient()), now) }
        }
        assertThrows(WarmFailure::class.java) {
            WarmRules.validateCampaign(campaign(listOf(recipient(), recipient(2))), listOf(recipient(), recipient(2)), now)
        }
    }
    @Test fun queueRecordsOpenedEditorAndSkipsWithoutClaimingSent() {
        val clients = (1..3).map { recipient(it, "https://doradca.pro/c/" + it.toString().padStart(43, 'A')) }
        val queue = WarmQueue(campaign(clients))
        assertEquals(0, queue.nextPending())
        queue.opened(0, now)
        assertEquals(WarmDraftState.EDITOR_OPENED, queue.state(0))
        assertThrows(WarmFailure::class.java) { queue.opened(0, now) }
        queue.opened(0, now, repeatConfirmed = true)
        queue.skip(1)
        assertEquals(2, queue.nextPending())
        queue.opened(2, now)
        assertNull(queue.nextPending())
    }
    @Test fun individualEditsPreserveAssignedLinkAndDoNotModifyOtherDrafts() {
        val clients = listOf(recipient(), recipient(2, "https://doradca.pro/c/" + "B".repeat(43)))
        val queue = WarmQueue(campaign(clients))
        val second = queue.body(1)
        queue.edit(0, "Cześć Test, inna treść ${clients[0].clickUrl}", now)
        assertTrue(queue.body(0).contains("inna treść"))
        assertEquals(second, queue.body(1))
        assertThrows(WarmFailure::class.java) { queue.edit(0, "Brak linku", now) }
        assertThrows(WarmFailure::class.java) { queue.edit(0, "Inny link ${clients[1].clickUrl}", now) }
        assertThrows(WarmFailure::class.java) { queue.opened(0, now.plusSeconds(3601)) }
    }
    @Test fun sessionKeepsIdempotencyForSameContactsAndClearsQueue() {
        val session = DraftSession()
        val first = listOf(recipient(), recipient(2))
        val id = session.requestId(first)
        assertEquals(id, session.requestId(first.reversed()))
        assertNotEquals(id, session.requestId(listOf(recipient())))
        session.warmQueue = WarmQueue(campaign())
        session.clear()
        assertNull(session.warmQueue)
        assertNotEquals(id, session.requestId(first))
    }
}
