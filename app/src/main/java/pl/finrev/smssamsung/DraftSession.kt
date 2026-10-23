package pl.finrev.smssamsung

import androidx.lifecycle.ViewModel

/** Deliberately not SavedStateHandle: a killed process loses contacts and drafts. */
class DraftSession : ViewModel() {
    val batch = RecipientBatch()
    var body = ""
    var mode: CommunicationMode? = null
    var publicProfile = ""
    var material: Material? = null
    var selectedPhone: String? = null
        private set
    fun selected(): Recipient? = batch.all().singleOrNull { it.phone == selectedPhone }
    fun select(phone: String) {
        val canonical = Phones.normalize(phone)
        require(batch.all().any { it.phone == canonical })
        selectedPhone = canonical; changed()
    }
    fun replaceRecipients(recipients: List<Recipient>) {
        batch.replace(recipients)
        if (selected() == null) selectedPhone = null
        changed()
    }
    fun addPicked(recipient: Recipient) {
        val canonical = Phones.normalize(recipient.phone)
        replaceRecipients(batch.all().filter { it.phone != canonical } + recipient)
        select(canonical)
    }
    fun removeSelected() {
        val selected = selectedPhone ?: return
        replaceRecipients(batch.all().filter { it.phone != selected })
    }
    fun changeMode(chosen: CommunicationMode) {
        if (mode == chosen) return
        mode = chosen; body = ""; clearMaterial()
    }
    fun clearMaterial() {
        material = null; publicProfile = ""; changed()
    }
    var revision = 0L
        private set
    fun changed() { revision++ }
    fun clear() {
        batch.clear(); body = ""; mode = null; publicProfile = ""; material = null; selectedPhone = null; changed()
    }
    override fun onCleared() { clear() }
}
