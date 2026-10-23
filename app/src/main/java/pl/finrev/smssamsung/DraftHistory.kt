package pl.finrev.smssamsung

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DraftHistory(context: Context) {
    private val prefs = context.getSharedPreferences("draft_history", Context.MODE_PRIVATE)
    private fun rows(): JSONArray = try { JSONArray(prefs.getString("items", "[]")) }
        catch (_: Exception) { JSONArray() }
    fun clear() { prefs.edit().clear().apply() }
    fun record(count: Int) {
        require(count in 1..MAX_RECIPIENTS)
        val old = rows()
        val clean = JSONArray()
        // Rebuild a strict allowlist; never carry unknown fields into storage.
        for (i in maxOf(0, old.length() - 29) until old.length()) {
            val row = old.optJSONObject(i) ?: continue
            val size = row.optInt("recipients")
            val time = row.optLong("timestamp")
            if (size in 1..MAX_RECIPIENTS && time > 0)
                clean.put(JSONObject().put("timestamp", time).put("recipients", size))
        }
        clean.put(JSONObject().put("timestamp", System.currentTimeMillis()).put("recipients", count))
        prefs.edit().putString("items", clean.toString()).apply()
    }
    fun display(): String {
        val rows = rows()
        return (rows.length() - 1 downTo maxOf(0, rows.length() - 20)).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("pl-PL"))
                .format(Date(row.optLong("timestamp")))
            "$date — ${row.optInt("recipients")} odbiorców (szkic)"
        }.joinToString("\n").ifEmpty { "Brak historii" }
    }
}
