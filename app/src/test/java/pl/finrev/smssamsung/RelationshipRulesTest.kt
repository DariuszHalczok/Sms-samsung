package pl.finrev.smssamsung

import org.junit.Assert.*
import org.junit.Test

class RelationshipRulesTest {
    @Test fun unknownModeAndMarketingAlwaysFailClosedIncludingSelfAttestation() {
        for (requested in listOf(false, true)) for (material in listOf(null, Material.IKZE)) {
            assertThrows(IllegalArgumentException::class.java) { DraftPolicy.requireAllowed(null, requested, material) }
            assertThrows(IllegalArgumentException::class.java) { DraftPolicy.requireAllowed(CommunicationMode.MARKETING, requested, material) }
        }
    }
    @Test fun requestedMaterialNeedsSpecificPriorRequestAndPrivateModeDoesNotAttachMaterials() {
        DraftPolicy.requireAllowed(CommunicationMode.PRIVATE, false, null)
        DraftPolicy.requireAllowed(CommunicationMode.REQUESTED, true, Material.IKZE)
        assertThrows(IllegalArgumentException::class.java) { DraftPolicy.requireAllowed(CommunicationMode.REQUESTED, false, Material.IKZE) }
        assertThrows(IllegalArgumentException::class.java) { DraftPolicy.requireAllowed(CommunicationMode.REQUESTED, true, null) }
        assertThrows(IllegalArgumentException::class.java) { DraftPolicy.requireAllowed(CommunicationMode.PRIVATE, true, Material.IKZE) }
    }
    @Test fun materialLinksAreIndependentOfRecipientAndRejectTrackedUrlsInManualDrafts() {
        val link = Material.IKZE.url("11111111-1111-4111-8111-111111111111")
        val bodies = listOf(Recipient("+12025550101", "Test One"), Recipient("+12025550102", "Test Two"))
            .map { Templates.render("Materiał: {link}", it, link) }
        assertEquals(bodies[0], bodies[1])
        for (url in listOf("https://doradca.pro/c/recipient-token", "$link?phone=example", "https://evil.test/x", "www.evil.test")) {
            assertThrows(IllegalArgumentException::class.java) { Templates.render(url, Recipient("+12025550101", "Test")) }
        }
    }
    @Test fun profileCannotCarryPhoneNameTokenOrArbitraryEndpoint() {
        for (value in listOf("+12025550101", "Test Example", "https://evil.test", "11111111-1111-4111-8111-111111111111?token=x")) {
            assertThrows(IllegalArgumentException::class.java) { PublicProfile.validate(value) }
        }
    }
}
