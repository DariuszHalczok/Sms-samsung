package pl.finrev.smssamsung

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var recipients: TextView
    private lateinit var search: EditText
    private lateinit var message: EditText
    private val selected = linkedMapOf<String, String>()
    private val speechCode = 100
    private val importCode = 102
    private val permissionCode = 101
    private var pendingSearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        fun button(label: String, click: () -> Unit) =
            Button(this).apply { text = label; setOnClickListener { click() }; layout.addView(this) }
        layout.addView(TextView(this).apply { text = "SMS Samsung — Asystent"; textSize = 24f })
        layout.addView(TextView(this).apply {
            text = "Kontakty pozostają na telefonie. Każda wiadomość wymaga osobnego zatwierdzenia w aplikacji SMS."
        })
        search = EditText(this).apply { hint = "Wyszukaj osoby lub grupę po nazwie" }
        layout.addView(search)
        button("Znajdź kontakty / grupy") { ensureContactsAccess() }
        button("Importuj listę klientów z Doradca.pro (CSV)") { importCsv() }
        recipients = TextView(this).apply { text = "Odbiorcy: brak"; textSize = 16f }
        layout.addView(recipients)
        button("Wyczyść odbiorców") { selected.clear(); renderRecipients() }
        message = EditText(this).apply {
            hint = "Treść SMS lub wklej tekst przygotowany w ChatGPT"
            minLines = 4
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        layout.addView(message)
        button("Dyktuj wiadomość") {
            try {
                startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Podyktuj treść wiadomości")
                }, speechCode)
            } catch (_: Exception) { toast("Brak usługi rozpoznawania mowy") }
        }
        button("Sprawdź odbiorców i przygotuj SMS-y") { preview() }
        layout.addView(TextView(this).apply {
            text = "Udostępnij tekst z ChatGPT do SMS Samsung albo wklej go powyżej. Automatyczne połączenie z rozmową ChatGPT nie jest jeszcze dostępne."
        })
        setContentView(ScrollView(this).apply { addView(layout) })
        readIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(incoming: Intent?) {
        when {
            incoming?.action == Intent.ACTION_SEND && incoming.type == "text/plain" ->
                message.setText(incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            incoming?.action == Intent.ACTION_VIEW &&
                incoming.data?.scheme == "smssamsung" &&
                incoming.data?.host == "compose" -> {
                // Import draft text only; never accept recipients or send instructions from links.
                val body = incoming.data?.getQueryParameter("text").orEmpty()
                if (body.isNotBlank()) {
                    if (body.length > 4000) {
                        toast("Wiadomość jest za długa (maks. 4000 znaków)")
                    } else {
                        message.setText(body)
                        toast("Zaimportowano szkic. Wybierz odbiorców i sprawdź treść.")
                    }
                }
            }
        }
    }

    private fun importCsv() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "application/csv", "application/octet-stream"))
            }, importCode)
        } catch (_: Exception) { toast("Nie można otworzyć wyboru pliku") }
    }

    private fun importCsvData(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(131073)
                val count = input.read(buffer)
                if (count < 0) ByteArray(0) else {
                    if (count > 131072 || input.read() != -1) throw IllegalArgumentException("Plik przekracza 128 KB")
                    buffer.copyOf(count)
                }
            } ?: throw IllegalArgumentException("Nie można odczytać pliku")
            val rows = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
            if (rows.size < 2) throw IllegalArgumentException("Brak klientów w pliku")
            val delimiter = if (rows.first().count { it == ';' } > rows.first().count { it == ',' }) ';' else ','
            val headers = rows.first().split(delimiter).map { it.trim().lowercase(Locale.ROOT) }
            val phoneIndex = headers.indexOfFirst { it in listOf("phone", "telefon", "numer", "mobile") }
            val nameIndex = headers.indexOfFirst { it in listOf("name", "imie", "imię", "klient", "nazwisko") }
            if (phoneIndex < 0) throw IllegalArgumentException("Brak kolumny phone lub telefon")
            val imported = linkedMapOf<String, String>()
            for (line in rows.drop(1)) {
                val cells = line.split(delimiter).map { it.trim().trim('"') }
                val rawPhone = cells.getOrNull(phoneIndex).orEmpty()
                val phone = normalizePhone(rawPhone)
                if (!phone.matches(Regex("\\+[0-9]{9,15}"))) continue
                imported[phone] = if (nameIndex >= 0) cells.getOrNull(nameIndex)?.take(100)?.ifBlank { phone } ?: phone else phone
                if (imported.size > 30) throw IllegalArgumentException("Maksymalnie 30 klientów na jedną partię")
            }
            if (imported.isEmpty()) throw IllegalArgumentException("Nie znaleziono poprawnych numerów")
            AlertDialog.Builder(this).setTitle("Import z Doradca.pro")
                .setMessage("Wczytano ${imported.size} unikalnych odbiorców. Zastąpić bieżącą listę?\n\nSprawdź zgody i cel wysyłki. Nic nie zostanie wysłane automatycznie.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Załaduj listę") { _, _ ->
                    selected.clear()
                    selected.putAll(imported)
                    renderRecipients()
                }.show()
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("Błąd importu")
                .setMessage(e.message ?: "Nieprawidłowy plik CSV")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun ensureContactsAccess() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
            chooseSearchType()
        else {
            pendingSearch = true
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), permissionCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && pendingSearch)
                chooseSearchType()
            else toast("Dostęp do kontaktów nie został przyznany")
            pendingSearch = false
        }
    }

    private fun chooseSearchType() {
        AlertDialog.Builder(this).setTitle("Co wyszukać?")
            .setItems(arrayOf("Osoby", "Grupy Google / telefonu")) { _, option ->
                if (option == 0) choosePeople() else chooseGroups()
            }.show()
    }

    private fun choosePeople() {
        val names = mutableListOf<String>()
        val numbers = mutableListOf<String>()
        val queryText = search.text.toString().trim()
        try {
            val selection = if (queryText.isBlank()) null else
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
            val args = if (queryText.isBlank()) null else arrayOf("%$queryText%")
            contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER), selection, args,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC")?.use { cursor ->
                while (cursor.moveToNext() && names.size < 300) {
                    val name = cursor.getString(0) ?: continue
                    val phone = cursor.getString(1) ?: continue
                    if (phone.count { it.isDigit() } >= 6) {
                        names.add("$name — $phone")
                        numbers.add(phone)
                    }
                }
            }
            if (names.isEmpty()) { toast("Nie znaleziono kontaktów"); return }
            val marks = BooleanArray(names.size) { selected.containsKey(normalizePhone(numbers[it])) }
            AlertDialog.Builder(this).setTitle("Wybierz odbiorców")
                .setMultiChoiceItems(names.toTypedArray(), marks) { _, i, checked -> marks[i] = checked }
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Dodaj") { _, _ ->
                    names.indices.forEach { i ->
                        if (marks[i]) selected[normalizePhone(numbers[i])] = names[i].substringBefore(" — ")
                        else selected.remove(normalizePhone(numbers[i]))
                    }
                    renderRecipients()
                }.show()
        } catch (_: SecurityException) { toast("Brak dostępu do kontaktów") }
    }

    private fun chooseGroups() {
        val ids = mutableListOf<Long>()
        val names = mutableListOf<String>()
        val q = search.text.toString().trim()
        contentResolver.query(ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups.DELETED}=0", null,
            ContactsContract.Groups.TITLE + " ASC")?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(1) ?: continue
                if (q.isBlank() || title.contains(q, ignoreCase = true)) {
                    ids.add(c.getLong(0)); names.add(title)
                }
            }
        }
        if (names.isEmpty()) { toast("Nie znaleziono grup"); return }
        val marks = BooleanArray(names.size)
        AlertDialog.Builder(this).setTitle("Wybierz grupy")
            .setMultiChoiceItems(names.toTypedArray(), marks) { _, i, checked -> marks[i] = checked }
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Dodaj wybrane") { _, _ ->
                ids.indices.forEach { i -> if (marks[i]) addGroup(ids[i]) }
                renderRecipients()
            }.show()
    }

    private fun addGroup(groupId: Long) {
        val rawIds = mutableSetOf<Long>()
        contentResolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
            "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID}=?",
            arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()), null
        )?.use { c -> while (c.moveToNext()) rawIds.add(c.getLong(0)) }
        if (rawIds.isEmpty()) return
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
        )?.use { c ->
            while (c.moveToNext()) if (c.getLong(0) in rawIds) {
                val phone = c.getString(2) ?: continue
                if (phone.count { it.isDigit() } >= 6)
                    selected[normalizePhone(phone)] = c.getString(1) ?: phone
            }
        }
        renderRecipients()
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 9 -> "+48$digits"
            digits.length == 11 && digits.startsWith("48") -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            phone.trim().startsWith("+") -> "+$digits"
            else -> digits
        }
    }

    private fun renderRecipients() {
        recipients.text = if (selected.isEmpty()) "Odbiorcy: brak" else
            "Odbiorcy (${selected.size}): " + selected.values.joinToString(", ")
    }

    private fun preview() {
        val body = message.text.toString().trim()
        if (selected.isEmpty()) { toast("Najpierw wybierz odbiorców"); return }
        if (body.isEmpty()) { message.error = "Podyktuj albo wpisz wiadomość"; return }
        val pairs = selected.entries.toList()
        AlertDialog.Builder(this).setTitle("Potwierdź przygotowanie ${pairs.size} SMS")
            .setMessage("Do: ${pairs.joinToString { it.value }.take(1100)}\n\nTreść:\n$body\n\nŻadna wiadomość nie zostanie wysłana automatycznie. Każdą zatwierdzasz osobno w aplikacji SMS.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Otwórz pierwszy SMS") { _, _ -> openSms(pairs, body, 0) }
            .show()
    }

    private fun openSms(pairs: List<Map.Entry<String, String>>, body: String, index: Int) {
        if (index >= pairs.size) { toast("Lista zakończona"); return }
        val (phone, name) = pairs[index]
        AlertDialog.Builder(this).setTitle("SMS ${index + 1} z ${pairs.size}: $name")
            .setMessage("Numer: $phone\n\n$body")
            .setNegativeButton("Zakończ", null)
            .setNeutralButton("Pomiń") { _, _ -> openSms(pairs, body, index + 1) }
            .setPositiveButton("Otwórz edytor") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts("smsto", phone.filter { it.isDigit() || it == '+' }, null)).apply {
                        putExtra("sms_body", body)
                    })
                    if (index + 1 < pairs.size) {
                        AlertDialog.Builder(this).setTitle("Następny odbiorca")
                            .setMessage("Po powrocie z aplikacji SMS możesz przygotować następną wiadomość. Aplikacja nie wie, czy poprzedni SMS został wysłany.")
                            .setPositiveButton("Następny") { _, _ -> openSms(pairs, body, index + 1) }
                            .setNegativeButton("Zakończ", null).show()
                    }
                } catch (_: Exception) { toast("Nie znaleziono aplikacji SMS") }
            }.show()
    }

    @Deprecated("Android speech result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == importCode && resultCode == RESULT_OK) {
            data?.data?.let { importCsvData(it) }
        }
        if (requestCode == speechCode && resultCode == RESULT_OK) {
            val dictated = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (dictated.isNotEmpty()) {
                val current = message.text.toString().trim()
                message.setText(if (current.isEmpty()) dictated else "$current $dictated")
            }
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
