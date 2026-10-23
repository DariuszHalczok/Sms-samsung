package pl.finrev.smssamsung

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

fun interface WarmTransport {
    fun post(token: String, requestId: String, body: ByteArray): ByteArray
}

/** Fixed endpoint from the new specification. No redirects, custom hosts, query or disk cache. */
class HttpsWarmTransport : WarmTransport {
    override fun post(token: String, requestId: String, body: ByteArray): ByteArray {
        if (token.length !in 20..8192 || token.any { it.code !in 33..126 }) throw WarmFailure(WarmError.AUTHORIZATION)
        val connection = (URL("https://doradca.pro/api/warm-market/init").openConnection() as HttpsURLConnection)
        try {
            connection.apply {
                requestMethod = "POST"; connectTimeout = 10000; readTimeout = 10000
                instanceFollowRedirects = false; useCaches = false; doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Idempotency-Key", UUID.fromString(requestId).toString())
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-store")
                setFixedLengthStreamingMode(body.size)
            }
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != 200) throw WarmFailure(when (connection.responseCode) {
                401 -> WarmError.AUTHORIZATION; 403 -> WarmError.FORBIDDEN
                409 -> WarmError.CONFLICT; 429 -> WarmError.RATE_LIMIT
                400, 413, 415, 422 -> WarmError.INVALID_DATA
                else -> WarmError.UNAVAILABLE
            })
            val mime = connection.contentType.orEmpty().substringBefore(';').trim()
            if (!mime.equals("application/json", true)) throw WarmFailure(WarmError.INVALID_DATA)
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= 131072) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer, 0, minOf(buffer.size, 131073 - output.size()))
                    if (count < 0) break
                    if (count == 0) continue
                    output.write(buffer, 0, count)
                }
                if (output.size() > 131072) throw WarmFailure(WarmError.INVALID_DATA)
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}

class WarmMarketApi(private val transport: WarmTransport = HttpsWarmTransport()) {
    fun initialize(contacts: List<Recipient>, token: String, requestId: String, now: Instant = Instant.now()): WarmCampaign {
        if (contacts.size !in 1..MAX_RECIPIENTS) throw WarmFailure(WarmError.INVALID_DATA)
        val normalized = RecipientBatch().apply { replace(contacts) }.all()
        if (normalized.any { it.name.isBlank() }) throw WarmFailure(WarmError.INVALID_DATA)
        val list = JSONArray()
        normalized.forEach { list.put(JSONObject().put("name", it.name).put("phone", it.phone)) }
        val body = JSONObject().put("contacts", list).toString().toByteArray(Charsets.UTF_8)
        if (body.size > 16384) throw WarmFailure(WarmError.INVALID_DATA)
        val response = try { transport.post(token, requestId, body) }
            catch (e: WarmFailure) { throw e }
            catch (_: Exception) { throw WarmFailure(WarmError.NETWORK) }
        return parse(response, normalized, now)
    }

    fun parse(bytes: ByteArray, requested: List<Recipient>, now: Instant): WarmCampaign {
        try {
            if (bytes.size > 131072) throw WarmFailure(WarmError.INVALID_DATA)
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            val root = JSONObject(text)
            val items = root.getJSONArray("recipients")
            if (items.length() !in 1..MAX_RECIPIENTS) throw WarmFailure(WarmError.INVALID_DATA)
            val recipients = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                Recipient(Phones.normalize(item.getString("phone")), item.getString("name"), item.getString("click_url"))
            }
            return WarmRules.validateCampaign(WarmCampaign(root.getString("campaign_id"),
                java.time.OffsetDateTime.parse(root.getString("expires_at")).toInstant(), root.getString("message_template"), recipients), requested, now)
        } catch (e: WarmFailure) { throw e }
        catch (_: Exception) { throw WarmFailure(WarmError.INVALID_DATA) }
    }
}
