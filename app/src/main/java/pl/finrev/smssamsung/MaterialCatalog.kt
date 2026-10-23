package pl.finrev.smssamsung

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection

/** No recipient, body, auth, cookie, query or analytics field enters this transport. */
fun interface CatalogTransport { fun fetch(publicProfile: String): ByteArray }

class HttpsCatalogTransport(
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) : CatalogTransport {
    override fun fetch(publicProfile: String): ByteArray {
        val id = PublicProfile.validate(publicProfile)
        val connection = open(URL("https://doradca.pro/api/relationship/catalog/$id"))
        try {
            connection.apply {
                requestMethod = "GET"; doOutput = false; useCaches = false; instanceFollowRedirects = false
                connectTimeout = 10000; readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-store")
                setRequestProperty("User-Agent", "SMS-Samsung-Catalog/0.6")
            }
            require(connection.responseCode == 200) { "Katalog niedostępny" }
            require(connection.contentType.orEmpty().substringBefore(';').trim().equals("application/json", true))
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(4096)
                while (out.size() <= 16384) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val n = input.read(buffer, 0, minOf(buffer.size, 16385 - out.size()))
                    if (n < 0) break
                    if (n > 0) out.write(buffer, 0, n)
                }
                require(out.size() <= 16384); out.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}

class MaterialCatalog(private val transport: CatalogTransport = HttpsCatalogTransport()) {
    fun load(publicProfile: String): List<Material> {
        val id = PublicProfile.validate(publicProfile)
        val bytes = transport.fetch(id)
        require(bytes.size <= 16384)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        val root = JSONObject(text)
        require(root.keys().asSequence().toSet() == setOf("public_profile", "materials"))
        require(root.getString("public_profile") == id)
        val rows = root.getJSONArray("materials")
        require(rows.length() in 1..Material.entries.size)
        val result = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            require(row.keys().asSequence().toSet() == setOf("id", "url"))
            val material = Material.entries.single { it.slug == row.getString("id") }
            require(row.getString("url") == material.url(id))
            DoradcaUrls.validate(row.getString("url"))
            material
        }
        require(result.distinct().size == result.size)
        return result
    }
}
