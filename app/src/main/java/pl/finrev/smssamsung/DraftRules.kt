package pl.finrev.smssamsung

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.net.URI

const val MAX_RECIPIENTS = 30
const val MAX_MESSAGE = 4000

// Deliberately exclude customer fields from generated toString/log messages.
class Recipient(val phone: String, val name: String) {
    override fun toString() = "Recipient(redacted)"
}

object Phones {
    private val util = PhoneNumberUtil.getInstance()

    fun normalize(raw: String): String {
        val text = raw.trim()
        require(text.length in 1..60 && text.matches(Regex("[+0-9 ()\\-.]+"))) {
            "Niepoprawny numer telefonu"
        }
        val compact = text.filter { it in '0'..'9' || it == '+' }
        require(compact.matches(Regex("\\+?[0-9]+"))) { "Niepoprawny numer telefonu" }
        val international = when {
            compact.startsWith("00") -> "+${compact.drop(2)}"
            compact.length == 11 && compact.startsWith("48") -> "+$compact"
            else -> compact
        }
        val parsed = try { util.parse(international, "PL") } catch (_: Exception) {
            throw IllegalArgumentException("Niepoprawny numer telefonu")
        }
        require(!parsed.hasExtension() && util.isValidNumber(parsed)) { "Niepoprawny numer telefonu" }
        return util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
    }
}

class RecipientBatch {
    private val entries = linkedMapOf<String, Recipient>()
    fun all(): List<Recipient> = entries.values.toList()
    fun clear() = entries.clear()

    // Validate the entire candidate before mutation; an error preserves the previous batch.
    fun replace(candidates: List<Recipient>) {
        val checked = linkedMapOf<String, Recipient>()
        for (candidate in candidates) {
            val phone = Phones.normalize(candidate.phone)
            require(candidate.name.length <= 100 && candidate.name.none { it.isISOControl() }) {
                "Niepoprawna nazwa odbiorcy"
            }
            val previous = checked[phone]
            require(previous == null || previous.name == candidate.name) {
                "Sprzeczne dane powtórzonego odbiorcy"
            }
            checked[phone] = Recipient(phone, candidate.name.trim())
            require(checked.size <= MAX_RECIPIENTS) { "Maksymalnie 30 odbiorców na paczkę" }
        }
        entries.clear()
        entries.putAll(checked)
    }
}

object DoradcaUrls {
    // Only stable public advisor/material addresses, never recipient codes.
    fun validate(value: String): URI {
        val uri = try { URI(value) } catch (_: Exception) {
            throw IllegalArgumentException("Niepoprawny adres Doradca.pro")
        }
        require(value.length <= 2048 && uri.scheme == "https" && uri.host == "doradca.pro" &&
            uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null &&
            uri.rawQuery == null && !uri.rawPath.orEmpty().contains('%') &&
            uri.path.orEmpty().matches(Regex("/edukacja/${PublicProfile.PATTERN}/(${Material.entries.joinToString("|") { it.slug }})"))) {
            "Wymagany bezpieczny adres HTTPS Doradca.pro bez danych w zapytaniu"
        }
        return uri
    }
}

object Templates {
    fun render(template: String, recipient: Recipient, publicLink: String = ""): String {
        require(template.isNotBlank() && template.length <= MAX_MESSAGE) { "Treść musi mieć od 1 do 4000 znaków" }
        val name = recipient.name.trim()
        val values = mapOf(
            "imie" to name.substringBefore(' '), "imię" to name.substringBefore(' '),
            "nazwisko" to name.substringAfter(' ', ""), "link" to publicLink
        )
        // A single substitution pass prevents placeholders inside names becoming instructions.
        val rendered = Regex("\\{([^{}]+)\\}").replace(template) { match ->
            val replacement = values[match.groupValues[1]]
            require(!replacement.isNullOrBlank()) { "Brak wartości pola szablonu lub nieznane pole" }
            if (match.groupValues[1] == "link") DoradcaUrls.validate(replacement)
            replacement
        }.trim()
        require(rendered.isNotEmpty() && rendered.length <= MAX_MESSAGE &&
            '{' !in rendered && '}' !in rendered && rendered.none { it.isISOControl() && it != '\n' && it != '\r' }) {
            "Niepoprawna treść lub wiadomość przekracza 4000 znaków"
        }
        Regex("(?i)(?:https?://|www\\.)\\S+").findAll(rendered).forEach { DoradcaUrls.validate(it.value) }
        return rendered
    }
}

data class SmsSize(val encoding: String, val units: Int, val segments: Int)

object SmsSizing {
    private val basic = ("@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ" +
        " ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà").toSet()
    private val extended = "\u000c^{}\\[~]|€".toSet()
    fun calculate(body: String): SmsSize {
        val gsm = body.all { it in basic || it in extended }
        val units = if (gsm) body.fold(0) { count, c -> count + if (c in extended) 2 else 1 } else body.length
        val single = if (gsm) 160 else 70
        val multi = if (gsm) 153 else 67
        return SmsSize(if (gsm) "GSM-7" else "Unicode", units,
            if (units == 0) 0 else if (units <= single) 1 else (units + multi - 1) / multi)
    }
}
