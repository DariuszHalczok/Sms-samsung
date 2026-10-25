package pl.finrev.smssamsung

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogContractTest {
    private val profile = "11111111-1111-4111-8111-111111111111"
    private fun fixture(url: String = Material.IKZE.url(profile)) = JSONObject().put("public_profile", profile)
        .put("materials", JSONArray().put(JSONObject().put("id", "ikze").put("url", url))).toString().toByteArray()
    @Test fun selectedContactsAndPrivateTextCannotEnterCatalogRequest() {
        val session = DraftSession().apply {
            batch.replace(listOf(Recipient("+12025550101", "Test Example"))); body = "Private synthetic text"
        }
        val requests = mutableListOf<String>()
        val catalog = MaterialCatalog { id -> requests.add(id); fixture() }
        assertEquals(listOf(Material.IKZE), catalog.load(profile))
        assertEquals(listOf(profile), requests)
        for (value in listOf(session.body, session.batch.all()[0].phone, session.batch.all()[0].name)) {
            assertFalse(requests.toString().contains(value))
        }
        session.clear()
    }
    @Test fun refusesRecipientTokensChangedProfilesExtraFieldsAndInvalidUtf8() {
        for (bytes in listOf(fixture("https://doradca.pro/c/individual-token"),
            fixture().toString(Charsets.UTF_8).replace(profile, "22222222-2222-4222-8222-222222222222").toByteArray(),
            JSONObject(fixture().toString(Charsets.UTF_8)).put("recipients", JSONArray()).toString().toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28), ByteArray(16385))) {
            assertThrows(Exception::class.java) { MaterialCatalog { bytes }.load(profile) }
        }
    }
    @Test fun httpsRequestIsGetWithoutBodyAuthQueryOrRedirects() {
        var connection: javax.net.ssl.HttpsURLConnection? = null
        val transport = HttpsCatalogTransport { url ->
            object : javax.net.ssl.HttpsURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getCipherSuite() = "test-only"
                override fun getLocalCertificates(): Array<java.security.cert.Certificate>? = null
                override fun getServerCertificates(): Array<java.security.cert.Certificate> = emptyArray()
                override fun getResponseCode() = 200
                override fun getContentType() = "application/json"
                override fun getInputStream() = java.io.ByteArrayInputStream(fixture())
                override fun getOutputStream(): java.io.OutputStream = throw AssertionError("Request body forbidden")
            }.also { connection = it }
        }
        assertEquals(listOf(Material.IKZE), MaterialCatalog(transport).load(profile))
        val request = connection!!
        assertEquals("https://doradca.pro/api/relationship/catalog/$profile", request.url.toString())
        assertEquals("GET", request.requestMethod)
        assertFalse(request.doOutput); assertFalse(request.instanceFollowRedirects); assertFalse(request.useCaches)
        assertNull(request.getRequestProperty("Authorization")); assertNull(request.getRequestProperty("Cookie"))
        assertNull(request.url.query); assertNull(request.url.ref); assertNull(request.url.userInfo)
    }
    @Test fun malformedPublicIdFailsBeforeAnyNetworkRequest() {
        var called = false
        assertThrows(IllegalArgumentException::class.java) { MaterialCatalog { called = true; fixture() }.load("+12025550101") }
        assertFalse(called)
    }
}
