package pl.finrev.smssamsung

import android.content.ContentResolver
import android.provider.ContactsContract

/** Transient, bounded reads. Call on a worker; never retain a second contact database. */
class ContactsSource(private val resolver: ContentResolver) {
    private fun checkCancelled() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun recipient(number: String?, name: String?): Recipient? {
        val phone = try { Phones.normalize(number.orEmpty()) } catch (_: IllegalArgumentException) { return null }
        val label = name.orEmpty().take(100)
        return if (label.none { it.isISOControl() }) Recipient(phone, label) else null
    }

    fun people(query: String): List<Recipient> {
        val found = linkedMapOf<String, Recipient>()
        resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Phone.NUMBER),
            if (query.isBlank()) null else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?",
            if (query.isBlank()) null else arrayOf("%$query%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC")?.use { cursor ->
            while (cursor.moveToNext() && found.size < 300) {
                checkCancelled()
                val row = recipient(cursor.getString(1), cursor.getString(0)) ?: continue
                found.putIfAbsent(row.phone, row)
            }
        }
        return found.values.toList()
    }

    fun groups(query: String): List<Pair<Long, String>> {
        val result = mutableListOf<Pair<Long, String>>()
        resolver.query(ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups.DELETED}=0", null, ContactsContract.Groups.TITLE + " ASC")?.use { cursor ->
            while (cursor.moveToNext() && result.size < 300) {
                checkCancelled()
                val title = cursor.getString(1).orEmpty()
                if (query.isBlank() || title.contains(query, true)) result.add(cursor.getLong(0) to title)
            }
        }
        return result
    }

    fun groupMembers(ids: List<Long>): List<Recipient> {
        val found = linkedMapOf<String, Recipient>()
        for (id in ids) {
            checkCancelled()
            val rawIds = linkedSetOf<Long>()
            resolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID}=?",
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, id.toString()), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    checkCancelled()
                    rawIds.add(cursor.getLong(0))
                    require(rawIds.size <= 1000) { "Grupa jest zbyt duża" }
                }
            }
            if (rawIds.isEmpty()) continue
            resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    checkCancelled()
                    if (cursor.getLong(0) !in rawIds) continue
                    val row = recipient(cursor.getString(2), cursor.getString(1)) ?: continue
                    found.putIfAbsent(row.phone, row)
                    require(found.size <= MAX_RECIPIENTS) { "Maksymalnie 30 odbiorców" }
                }
            }
        }
        return found.values.toList()
    }
}
