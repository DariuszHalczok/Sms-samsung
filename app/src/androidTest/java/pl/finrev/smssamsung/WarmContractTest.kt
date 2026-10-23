package pl.finrev.smssamsung

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WarmContractTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private val contacts = listOf(Recipient("+12025550101", "Test Example"))
    private fun response(): JSONObject = JSONObject().put("campaign_id", UUID.randomUUID().toString())
        .put("expires_at", now.plusSeconds(3600).toString()).put("message_template", "Cześć {imie}, zapraszam: {link}")
        .put("recipients", JSONArray().put(JSONObject().put("name", "Test Example").put("phone", "+12025550101")
            .put("click_url", "https://doradca.pro/c/" + "A".repeat(43))))
    @Test fun postsOnlySelectedContactsWithStableIdempotencyAndParsesApprovedResponse() {
        val id = UUID.randomUUID().toString()
        val api = WarmMarketApi { _, key, bytes ->
            assertEquals(id, key)
            val request = JSONObject(bytes.toString(Charsets.UTF_8))
            assertEquals(setOf("contacts"), request.keys().asSequence().toSet())
            assertEquals(1, request.getJSONArray("contacts").length())
            assertEquals("+12025550101", request.getJSONArray("contacts").getJSONObject(0).getString("phone"))
            response().toString().toByteArray()
        }
        val campaign = api.initialize(contacts, "synthetic-session-only", id, now)
        assertTrue(Templates.render(campaign.template, campaign.recipients.single()).contains("Test"))
    }
    @Test fun rejectsInvalidUtf8TruncationMissingFieldsExpiredLinksAndOversizedResponses() {
        val api = WarmMarketApi { _, _, _ -> error("No network expected") }
        val invalid = listOf(byteArrayOf(0xc3.toByte(), 0x28), "{".toByteArray(), ByteArray(131073),
            response().put("expires_at", now.minusSeconds(1).toString()).toString().toByteArray(),
            response().put("recipients", JSONArray()).toString().toByteArray())
        invalid.forEach { bytes -> assertThrows(WarmFailure::class.java) { api.parse(bytes, contacts, now) } }
    }
    @Test fun unauthorizedAndOfflineResultsAreNotSuccess() {
        for (error in listOf(WarmError.AUTHORIZATION, WarmError.FORBIDDEN, WarmError.CONFLICT, WarmError.RATE_LIMIT)) {
            val api = WarmMarketApi { _, _, _ -> throw WarmFailure(error) }
            val thrown = assertThrows(WarmFailure::class.java) { api.initialize(contacts, "synthetic-session-only", UUID.randomUUID().toString(), now) }
            assertEquals(error, thrown.kind)
        }
        val api = WarmMarketApi { _, _, _ -> throw java.io.IOException() }
        assertEquals(WarmError.NETWORK, assertThrows(WarmFailure::class.java) {
            api.initialize(contacts, "synthetic-session-only", UUID.randomUUID().toString(), now)
        }.kind)
    }
    @Test fun warmQueueAndIndividualEditSurviveRotationOnlyInMemory() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val session = androidx.lifecycle.ViewModelProvider(activity)[DraftSession::class.java]
                val campaign = WarmMarketApi().parse(response().toString().toByteArray(), contacts, now)
                session.batch.replace(campaign.recipients)
                session.warmQueue = WarmQueue(campaign).apply { rememberEdit(0, "Niedokończona indywidualna edycja") }
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val queue = androidx.lifecycle.ViewModelProvider(activity)[DraftSession::class.java].warmQueue!!
                assertEquals("Niedokończona indywidualna edycja", queue.buffer(0))
                assertFalse(activity.findViewById<android.widget.EditText>(R.id.message).isEnabled)
                activity.findViewById<android.view.View>(R.id.clear_session).performClick()
                assertNull(androidx.lifecycle.ViewModelProvider(activity)[DraftSession::class.java].warmQueue)
            }
        }
    }

    @Test fun acceptsServerUtcOffsetAndEstimatesPlatformSegmentsWithoutSending() {
        val payload = response().put("expires_at", "2026-10-23T13:00:00+00:00")
        assertEquals(now.plusSeconds(3600), WarmMarketApi().parse(payload.toString().toByteArray(), contacts, now).expiresAt)
        assertEquals(2, AndroidSmsSizing.calculate("a".repeat(161)).segments)
        assertEquals(2, AndroidSmsSizing.calculate("ą".repeat(71)).segments)
    }

}
