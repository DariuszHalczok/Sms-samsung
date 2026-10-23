package pl.finrev.smssamsung

import java.time.Instant
import java.util.UUID

/** Server-issued data only. No customer fields in generated debug/log output. */
class WarmCampaign(val id: String, val expiresAt: Instant, val template: String, val recipients: List<Recipient>) {
    override fun toString() = "WarmCampaign(redacted)"
}

enum class WarmError { AUTHORIZATION, FORBIDDEN, CONFLICT, RATE_LIMIT, INVALID_DATA, EXPIRED, NETWORK, UNAVAILABLE }
class WarmFailure(val kind: WarmError) : IllegalArgumentException(kind.name)

object WarmRules {
    fun validateCampaign(campaign: WarmCampaign, requested: List<Recipient>, now: Instant): WarmCampaign {
        val canonical = try { UUID.fromString(campaign.id).toString() } catch (_: Exception) { throw WarmFailure(WarmError.INVALID_DATA) }
        if (canonical != campaign.id || campaign.recipients.size !in 1..MAX_RECIPIENTS ||
            campaign.template.length > MAX_MESSAGE || !campaign.template.contains("{link}") ||
            campaign.expiresAt > now.plusSeconds(7 * 86400L + 300)) throw WarmFailure(WarmError.INVALID_DATA)
        if (campaign.expiresAt <= now) throw WarmFailure(WarmError.EXPIRED)
        val expected = requested.associateBy { Phones.normalize(it.phone) }
        if (expected.size != campaign.recipients.size) throw WarmFailure(WarmError.INVALID_DATA)
        val seen = mutableSetOf<String>()
        val links = mutableSetOf<String>()
        for (recipient in campaign.recipients) {
            val phone = Phones.normalize(recipient.phone)
            if (!seen.add(phone) || expected[phone]?.name?.trim() != recipient.name || recipient.name.length > 100 ||
                recipient.name.any { it.isISOControl() }) throw WarmFailure(WarmError.INVALID_DATA)
            val uri = DoradcaUrls.validate(recipient.clickUrl)
            if (!uri.rawPath.matches(Regex("/c/[A-Za-z0-9_-]{43}")) || !links.add(recipient.clickUrl))
                throw WarmFailure(WarmError.INVALID_DATA)
            Templates.render(campaign.template, recipient)
        }
        return campaign
    }
}

enum class WarmDraftState { DRAFT, EDITOR_OPENED, SKIPPED }

class WarmQueue(val campaign: WarmCampaign) {
    private val bodies = campaign.recipients.map { Templates.render(campaign.template, it) }.toMutableList()
    private val buffers = bodies.toMutableList()
    private val states = MutableList(campaign.recipients.size) { WarmDraftState.DRAFT }
    fun state(index: Int) = states[index]
    fun body(index: Int) = bodies[index]
    fun buffer(index: Int) = buffers[index]
    fun rememberEdit(index: Int, text: String) {
        if (text.length > MAX_MESSAGE) throw WarmFailure(WarmError.INVALID_DATA)
        buffers[index] = text
    }
    fun nextPending(): Int? = states.indices.firstOrNull { states[it] == WarmDraftState.DRAFT }
    fun ensureActive(now: Instant) {
        if (campaign.expiresAt <= now) throw WarmFailure(WarmError.EXPIRED)
    }
    fun edit(index: Int, text: String, now: Instant) {
        ensureActive(now)
        val recipient = campaign.recipients[index]
        val rendered = Templates.render(text, recipient)
        val found = Regex("https://doradca\\.pro/c/[A-Za-z0-9_-]+").findAll(rendered).map { it.value }.toList()
        if (found != listOf(recipient.clickUrl)) throw WarmFailure(WarmError.INVALID_DATA)
        bodies[index] = rendered
        buffers[index] = rendered
    }
    fun skip(index: Int) { states[index] = WarmDraftState.SKIPPED }
    fun opened(index: Int, now: Instant, repeatConfirmed: Boolean = false) {
        ensureActive(now)
        if (states[index] == WarmDraftState.EDITOR_OPENED && !repeatConfirmed) throw WarmFailure(WarmError.CONFLICT)
        states[index] = WarmDraftState.EDITOR_OPENED
    }
    override fun toString() = "WarmQueue(redacted)"
}
