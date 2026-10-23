package pl.finrev.smssamsung

import androidx.lifecycle.ViewModel

/** Deliberately not SavedStateHandle: a killed process loses contacts and drafts. */
class DraftSession : ViewModel() {
    val batch = RecipientBatch()
    var body = ""
    var mode: CommunicationMode? = null
    var publicProfile = ""
    var material: Material? = null
    var revision = 0L
        private set
    fun changed() { revision++ }
    fun clear() {
        batch.clear(); body = ""; mode = null; publicProfile = ""; material = null; changed()
    }
    override fun onCleared() { clear() }
}
