package pl.finrev.smssamsung

import android.content.Context

/** Only the advisor's public identifier survives a session. Never contacts, drafts or credentials. */
class AdvisorProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("advisor_public_profile", Context.MODE_PRIVATE)
    fun load(): String? {
        val value = try { preferences.getString("public_id", null) } catch (_: ClassCastException) { null }
        if (value != null && runCatching { PublicProfile.validate(value) }.isSuccess) {
            if (preferences.all.keys != setOf("public_id")) save(value)
            return value
        }
        if (preferences.all.isNotEmpty()) preferences.edit().clear().apply()
        return null
    }
    fun save(publicId: String) {
        val valid = PublicProfile.validate(publicId)
        preferences.edit().clear().putString("public_id", valid).apply()
    }
    fun clear() { preferences.edit().clear().apply() }
}
