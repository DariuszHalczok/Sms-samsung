package pl.finrev.smssamsung

import androidx.lifecycle.ViewModel

class DraftSession : ViewModel() {
    val batch = RecipientBatch()
    var body = ""
    var search = ""
    var revision = 0L
        private set
    fun changed() { revision++ }
    fun clear() {
        batch.clear()
        body = ""
        search = ""
        changed()
    }
    override fun onCleared() { clear() }
}
