package pl.finrev.smssamsung

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone

/** Query only the single Phone row granted by the picker; never enumerate contacts. */
object ContactPicker {
    fun intent() = Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)
    fun read(resolver: ContentResolver, uri: Uri): Recipient {
        require(uri.scheme == "content" && uri.authority == "com.android.contacts")
        require(uri.query == null && uri.fragment == null)
        require(uri.path.orEmpty().matches(Regex("/data/(?:phones/)?[0-9]+")))
        return resolver.query(uri, arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME), null, null, null)?.use { cursor ->
            require(cursor.count == 1 && cursor.moveToFirst())
            val phone = Phones.normalize(cursor.getString(0).orEmpty())
            val name = cursor.getString(1).orEmpty()
            RecipientBatch().apply { replace(listOf(Recipient(phone, name))) }.all().single()
        } ?: throw IllegalArgumentException("Kontakt niedostępny")
    }
}
