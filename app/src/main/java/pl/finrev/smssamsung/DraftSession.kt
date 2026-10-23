package pl.finrev.smssamsung

import androidx.lifecycle.ViewModel

class DraftSession : ViewModel() {
    val batch = RecipientBatch()
    var body = ""
    var search = ""
    var warmQueue: WarmQueue? = null
    private var warmFingerprint = ""
    private var warmRequestId = ""
    fun requestId(contacts: List<Recipient>): String {
        val canonical = contacts.sortedBy { it.phone }.joinToString("\u0000") { it.phone + "\u0001" + it.name }
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
        if (fingerprint != warmFingerprint) {
            warmFingerprint = fingerprint
            warmRequestId = java.util.UUID.randomUUID().toString()
        }
        return warmRequestId
    }
    var revision = 0L
        private set
    fun changed() { revision++ }
    fun clear() {
        warmQueue = null
        warmFingerprint = ""; warmRequestId = ""
        batch.clear()
        body = ""
        search = ""
        changed()
    }
    override fun onCleared() { clear() }
}
