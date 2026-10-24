package pl.finrev.smssamsung

import android.os.SystemClock

/** Text is handed to the editor only in process memory, never through an exported intent extra. */
internal object SharedDraft {
    private var body: String? = null
    private var created = 0L
    @Synchronized fun put(value: String) {
        require(value.isNotBlank() && value.length <= MAX_MESSAGE)
        body = value; created = SystemClock.elapsedRealtime()
    }
    @Synchronized fun take(): String? {
        val result = body.takeIf { SystemClock.elapsedRealtime() - created <= 30_000 }
        clear()
        return result
    }
    @Synchronized fun clear() { body = null; created = 0L }
}
