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
import android.os.StrictMode
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var recipients: TextView
    private lateinit var search: EditText
    private lateinit var message: EditText
    private val selected = linkedMapOf<String, String>()
    private val links = linkedMapOf<String, String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("draft_history", MODE_PRIVATE) }
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
        button("Wyczyść odbiorców") { selected.clear(); links.clear(); renderRecipients() }
        button("Pobierz paczkę z API Doradca.pro") { showApiDialog() }
        message = EditText(this).apply {
            hint = "Treść SMS lub wklej tekst przygotowany w ChatGPT"
            minLines = 4
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        layout.addView(message)
        button("Szablony: hipoteki / polisy / IKZE") { chooseTemplate() }
        button("Historia przygotowanych paczek") { showHistory() }
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
                    links.clear()
                    selected.putAll(imported)
                    renderRecipients()
                }.show()
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("Błąd importu")
                .setMessage(e.message ?: "Nieprawidłowy plik CSV")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun chooseTemplate() {
        val templates = arrayOf("Hipoteka", "Polisa", "IKZE")
        val texts = arrayOf(
            "Dzień dobry {imie}, zapraszam do przeglądu warunków kredytu hipotecznego. Możemy umówić krótką rozmowę. {link}",
            "Dzień dobry {imie}, proponuję przegląd aktualnej ochrony ubezpieczeniowej. Czy znajdziemy dogodny termin rozmowy? {link}",
            "Dzień dobry {imie}, możemy omówić możliwości oszczędzania na emeryturę i IKZE. Zapraszam do kontaktu. {link}"
        )
        AlertDialog.Builder(this).setTitle("Wybierz szablon")
            .setItems(templates) { _, i -> message.setText(texts[i]) }.show()
    }

    private fun personalized(body: String, phone: String, name: String): String {
        val firstName = name.trim().substringBefore(" ").take(60)
        val link = links[phone].orEmpty()
        return body.replace("{imie}", firstName).replace("{imię}", firstName)
            .replace("{nazwisko}", name.trim().substringAfter(" ", ""))
            .replace("{link}", link).trim()
    }

    private fun recordDraft(size: Int) {
        // Intentionally do not persist names, numbers, texts or click URLs.
        val history = org.json.JSONArray(prefs.getString("items", "[]"))
        val row = JSONObject().put("timestamp", System.currentTimeMillis()).put("recipients", size)
        history.put(row)
        val trimmed = org.json.JSONArray()
        for (i in maxOf(0, history.length() - 30) until history.length()) trimmed.put(history.get(i))
        prefs.edit().putString("items", trimmed.toString()).apply()
    }

    private fun showHistory() {
        val rows = org.json.JSONArray(prefs.getString("items", "[]"))
        val lines = (rows.length() - 1 downTo maxOf(0, rows.length() - 20)).map { i ->
            val item = rows.getJSONObject(i)
            val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl", "PL"))
                .format(java.util.Date(item.getLong("timestamp")))
            "$date — ${item.getInt("recipients")} odbiorców (szkic)"
        }
        AlertDialog.Builder(this).setTitle("Historia przygotowanych paczek")
            .setMessage(lines.joinToString("\n").ifEmpty { "Brak historii" })
            .setNeutralButton("Wyczyść historię") { _, _ -> prefs.edit().remove("items").apply() }
            .setPositiveButton("Zamknij", null).show()
    }

    private fun showApiDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 4)
        }
        val endpoint = EditText(this).apply { hint = "https://doradca.pro/.../campaigns/ID"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI }
        val token = EditText(this).apply { hint = "Token dostępu (tylko w tej sesji)"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        layout.addView(endpoint); layout.addView(token)
        AlertDialog.Builder(this).setTitle("API Doradca.pro — pobierz paczkę")
            .setMessage("Wprowadź zatwierdzony adres endpointu HTTPS i token. Aplikacja nie zapisuje tokenu ani nie wysyła SMS-ów.")
            .setView(layout).setNegativeButton("Anuluj", null)
            .setPositiveButton("Pobierz") { _, _ ->
                val url = endpoint.text.toString().trim()
                val auth = token.text.toString()
                token.text.clear()
                if (!url.startsWith("https://") || Uri.parse(url).host != "doradca.pro" || auth.isBlank()) {
                    toast("Wymagany HTTPS na doradca.pro i token")
                } else fetchCampaign(url, auth)
            }.show()
    }

    private fun fetchCampaign(endpoint: String, token: String) {
        executor.execute {
            try {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 10000; readTimeout = 10000
                    instanceFollowRedirects = false
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode != 200) throw IllegalArgumentException("Odpowiedź HTTP ${connection.responseCode}")
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.size > 131072) throw IllegalArgumentException("Paczka przekracza 128 KB")
                    val root = JSONObject(bytes.toString(Charsets.UTF_8))
                    val list = root.getJSONArray("recipients")
                    if (list.length() !in 1..30) throw IllegalArgumentException("Paczka musi mieć od 1 do 30 odbiorców")
                    val found = linkedMapOf<String, String>()
                    val clickLinks = linkedMapOf<String, String>()
                    for (i in 0 until list.length()) {
                        val client = list.getJSONObject(i)
                        val phone = normalizePhone(client.getString("phone"))
                        if (!phone.matches(Regex("\\+[0-9]{9,15}"))) throw IllegalArgumentException("Niepoprawny numer telefonu")
                        found[phone] = client.optString("name", phone).take(100)
                        val click = client.optString("click_url")
                        if (click.isNotBlank()) {
                            val uri = Uri.parse(click)
                            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null)
                                throw IllegalArgumentException("Niepoprawny link kliknięć")
                            clickLinks[phone] = click
                        }
                    }
                    val draft = root.optString("message_template", "").take(4000)
                    runOnUiThread {
                        AlertDialog.Builder(this).setTitle("Dane z Doradca.pro")
                            .setMessage("Pobrano ${found.size} odbiorców. Załadować paczkę do edytora? Przed wysyłką sprawdzisz każdą wiadomość.")
                            .setNegativeButton("Anuluj", null)
                            .setPositiveButton("Załaduj") { _, _ ->
                                selected.clear(); selected.putAll(found)
                                links.clear(); links.putAll(clickLinks)
                                if (draft.isNotBlank()) message.setText(draft)
                                renderRecipients()
                            }.show()
                    }
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                runOnUiThread { AlertDialog.Builder(this).setTitle("API niedostępne")
                    .setMessage(e.message ?: "Błąd połączenia").setPositiveButton("OK", null).show() }
            }
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
        val template = message.text.toString().trim()
        if (selected.isEmpty()) { toast("Najpierw wybierz odbiorców"); return }
        if (template.isEmpty()) { message.error = "Podyktuj albo wpisz wiadomość"; return }
        if (selected.size > 30) { toast("Maksymalnie 30 odbiorców na paczkę"); return }
        val pairs = selected.entries.map { Triple(it.key, it.value, personalized(template, it.key, it.value)) }
        if (pairs.any { it.third.contains("{link}") || it.third.length > 4000 }) {
            toast("Brakuje linku dla odbiorcy lub wiadomość jest za długa"); return
        }
        val overview = pairs.take(4).joinToString("\n\n") { (phone, name, body) -> "$name ($phone):\n$body" }
        AlertDialog.Builder(this).setTitle("Sprawdź ${pairs.size} szkiców SMS")
            .setMessage("$overview\n\nKażdy SMS wymaga osobnego zatwierdzenia w systemowej aplikacji.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Przygotuj") { _, _ ->
                recordDraft(pairs.size)
                openSms(pairs, 0)
            }.show()
    }

    private fun openSms(pairs: List<Triple<String, String, String>>, index: Int) {
        if (index >= pairs.size) { toast("Lista zakończona"); return }
        val (phone, name, body) = pairs[index]
        AlertDialog.Builder(this).setTitle("SMS ${index + 1} z ${pairs.size}: $name")
            .setMessage("Numer: $phone\n\n$body")
            .setNegativeButton("Zakończ", null)
            .setNeutralButton("Pomiń") { _, _ -> openSms(pairs, index + 1) }
            .setPositiveButton("Otwórz edytor") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts("smsto", phone, null)).apply { putExtra("sms_body", body) })
                    if (index + 1 < pairs.size) {
                        AlertDialog.Builder(this).setTitle("Kolejny odbiorca")
                            .setMessage("Po powrocie z aplikacji SMS możesz otworzyć kolejny szkic. Nie potwierdzamy wysłania ani doręczenia.")
                            .setPositiveButton("Następny") { _, _ -> openSms(pairs, index + 1) }
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
