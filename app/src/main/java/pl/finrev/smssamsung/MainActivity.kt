package pl.finrev.smssamsung

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity : ComponentActivity() {
    private val session: DraftSession by viewModels()
    private val history by lazy { DraftHistory(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private var work: Future<*>? = null
    private var dialog: AlertDialog? = null
    private lateinit var recipients: TextView
    private lateinit var search: EditText
    private lateinit var message: EditText
    private var operation = 0L
    private val speechCode = 100
    private val permissionCode = 101
    private val importCode = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        fun button(label: String, viewId: Int? = null, action: () -> Unit) = Button(this).apply {
            text = label
            viewId?.let { id = it }
            setOnClickListener { action() }
            layout.addView(this)
        }
        layout.addView(TextView(this).apply { text = "SMS Samsung 0.4 — szkice"; textSize = 24f })
        layout.addView(TextView(this).apply {
            text = "Doradca.pro pozostaje CRM. Paczka jest tylko w pamięci sesji. Każdy SMS zatwierdzasz w aplikacji SMS. Nie potwierdzamy wysłania ani doręczenia."
        })
        search = EditText(this).apply {
            id = R.id.search; hint = "Wyszukaj osoby lub grupę"; isSaveEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(100)); setText(session.search)
            watch { session.search = it }
        }
        layout.addView(search)
        button("Znajdź kontakty / grupy") { ensureContactsAccess() }
        button("Import CSV z Doradca.pro") { importCsv() }
        recipients = TextView(this).apply { id = R.id.recipients; textSize = 16f; isSaveEnabled = false }
        layout.addView(recipients)
        button("Wyczyść sesję", R.id.clear_session) { clearSession() }
        button("Doradca.pro — status integracji") {
            show(AlertDialog.Builder(this).setTitle("Integracja oczekuje na backend")
                .setMessage("Brak zweryfikowanego API kampanii, OAuth/parowania i serwerowego potwierdzenia zgód. Pobieranie API i linki kampanii są wyłączone. Użyj zatwierdzonego eksportu CSV. Nie wpisuj tutaj tokenów.")
                .setPositiveButton("OK", null))
        }
        message = EditText(this).apply {
            id = R.id.message; hint = "Treść SMS: {imie}, {nazwisko}, opcjonalnie {link}"; minLines = 4
            isSaveEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(MAX_MESSAGE)); setText(session.body)
            watch { session.body = it; session.changed() }
        }
        layout.addView(message)
        button("Szablony: hipoteka / polisa / IKZE") { chooseTemplate() }
        button("Historia przygotowanych paczek") {
            show(AlertDialog.Builder(this).setTitle("Historia szkiców — nie wysyłki")
                .setMessage(history.display()).setPositiveButton("Zamknij", null)
                .setNeutralButton("Wyczyść historię") { _, _ -> history.clear() })
        }
        button("Dyktuj wiadomość") {
            try {
                startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
                }, speechCode)
            } catch (_: Exception) { toast("Brak usługi rozpoznawania mowy") }
        }
        button("Sprawdź odbiorców i szkice", R.id.preview) { preview() }
        setContentView(ScrollView(this).apply {
            isSaveEnabled = false; addView(layout)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        })
        // Exported launcher deliberately ignores extras. Only our in-app share entry forwards text.
        acceptInternalDraft()
        renderRecipients()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptInternalDraft()
    }

    private fun acceptInternalDraft() {
        val body = SharedDraft.take() ?: return
        clearSession()
        message.setText(body)
    }

    private fun EditText.watch(update: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun show(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        if (isFinishing || isDestroyed) return
        dialog = builder.show().also { it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    private fun clearSession() {
        operation++
        work?.cancel(true)
        session.clear()
        search.text.clear(); message.text.clear()
        SharedDraft.clear()
        dialog?.dismiss()
        renderRecipients()
    }

    private fun importCsv() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "application/csv", "application/octet-stream"))
            }, importCode)
        } catch (_: Exception) { toast("Nie można otworzyć wyboru pliku") }
    }

    private fun <T> load(read: () -> T, done: (T) -> Unit) {
        val revision = session.revision
        val request = ++operation
        work?.cancel(true)
        work = executor.submit {
            try {
                val result = read()
                runOnUiThread {
                    if (!isDestroyed && !isFinishing && operation == request && session.revision == revision) done(result)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isDestroyed && !isFinishing && operation == request && session.revision == revision)
                        toast("Nie wczytano danych. Sprawdź uprawnienia, format pliku, numery i limit 30 osób.")
                }
            }
        }
    }

    private fun importCsvData(uri: Uri) {
        load({ contentResolver.openInputStream(uri)?.use { CsvImport.read(it) }
            ?: throw IllegalArgumentException("Nie można odczytać pliku") }) { imported ->
            show(AlertDialog.Builder(this).setTitle("Wczytano ${imported.size} odbiorców")
                .setMessage("Zastąpić bieżącą paczkę? Zgody, cel i uprawnienia sprawdzisz w Doradca.pro przed przygotowaniem szkiców.")
                .setNegativeButton("Anuluj", null).setPositiveButton("Załaduj") { _, _ -> updateRecipients(imported) })
        }
    }

    private fun chooseTemplate() {
        val texts = arrayOf(
            "Dzień dobry {imie}, zapraszam do przeglądu warunków kredytu hipotecznego. Czy możemy umówić krótką rozmowę?",
            "Dzień dobry {imie}, proponuję przegląd aktualnej ochrony ubezpieczeniowej. Czy znajdziemy dogodny termin rozmowy?",
            "Dzień dobry {imie}, możemy omówić możliwości oszczędzania na emeryturę i IKZE. Zapraszam do kontaktu."
        )
        show(AlertDialog.Builder(this).setTitle("Wybierz szablon")
            .setItems(arrayOf("Hipoteka", "Polisa", "IKZE")) { _, i -> message.setText(texts[i]) })
    }

    private fun ensureContactsAccess() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) chooseSearchType()
        else requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), permissionCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) chooseSearchType()
            else toast("Dostęp do kontaktów nie został przyznany")
        }
    }

    private fun chooseSearchType() {
        show(AlertDialog.Builder(this).setTitle("Co wyszukać?")
            .setItems(arrayOf("Osoby", "Grupy telefonu")) { _, i -> if (i == 0) choosePeople() else chooseGroups() })
    }

    private fun choosePeople() {
        val query = search.text.toString().trim()
        load({ ContactsSource(contentResolver).people(query) }) { items ->
            if (items.isEmpty()) { toast("Nie znaleziono poprawnych kontaktów"); return@load }
            val current = session.batch.all().associateBy { it.phone }
            val marks = BooleanArray(items.size) { items[it].phone in current }
            show(AlertDialog.Builder(this).setTitle("Wybierz odbiorców (maks. 30)")
                .setMultiChoiceItems(items.map { "${it.name} — ${it.phone}" }.toTypedArray(), marks) { _, i, checked -> marks[i] = checked }
                .setNegativeButton("Anuluj", null).setPositiveButton("Zatwierdź") { _, _ ->
                    val visible = items.map { it.phone }.toSet()
                    val candidate = current.values.filter { it.phone !in visible } +
                        items.filterIndexed { i, _ -> marks[i] }.map { current[it.phone] ?: it }
                    updateRecipients(candidate)
                })
        }
    }

    private fun chooseGroups() {
        val query = search.text.toString().trim()
        load({ ContactsSource(contentResolver).groups(query) }) { groups ->
            if (groups.isEmpty()) { toast("Nie znaleziono grup"); return@load }
            val marks = BooleanArray(groups.size)
            show(AlertDialog.Builder(this).setTitle("Wybierz grupy (łącznie maks. 30 osób)")
                .setMultiChoiceItems(groups.map { it.second }.toTypedArray(), marks) { _, i, checked -> marks[i] = checked }
                .setNegativeButton("Anuluj", null).setPositiveButton("Dodaj") { _, _ ->
                    val ids = groups.filterIndexed { i, _ -> marks[i] }.map { it.first }
                    load({ ContactsSource(contentResolver).groupMembers(ids) }) { members ->
                        val combined = session.batch.all().associateBy { it.phone }.toMutableMap()
                        members.forEach { combined.putIfAbsent(it.phone, it) }
                        updateRecipients(combined.values.toList())
                    }
                })
        }
    }

    private fun updateRecipients(candidate: List<Recipient>) {
        try { session.batch.replace(candidate); session.changed(); renderRecipients() }
        catch (_: IllegalArgumentException) { toast("Nie zmieniono paczki: niepoprawne dane, duplikaty lub więcej niż 30 odbiorców") }
    }

    private fun renderRecipients() {
        val list = session.batch.all()
        recipients.text = if (list.isEmpty()) "Odbiorcy: brak" else
            "Odbiorcy (${list.size}): " + list.joinToString(", ") { it.name.ifBlank { it.phone } }
    }

    private fun preview() {
        val clients = session.batch.all()
        if (clients.isEmpty()) { toast("Najpierw wybierz odbiorców"); return }
        val bodies = try { clients.map { Templates.render(session.body, it) } }
        catch (e: IllegalArgumentException) { message.error = e.message; return }
        val consent = CheckBox(this).apply {
            text = "Sprawdziłem w Doradca.pro uprawnienia, cel, aktualną podstawę kontaktu, brak sprzeciwu i powtórzeń dla całej paczki."
        }
        val builder = AlertDialog.Builder(this).setTitle("Potwierdź ${clients.size} szkiców")
            .setMessage("Telefon nie weryfikuje zgód ani kolizji w CRM. Każdego odbiorcę i treść zobaczysz przed otwarciem edytora SMS.")
            .setView(consent).setNegativeButton("Anuluj", null).setPositiveButton("Przeglądaj", null)
        show(builder)
        val confirmation = dialog ?: return
        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            consent.setOnCheckedChangeListener { _, checked -> isEnabled = checked }
            setOnClickListener {
                if (!consent.isChecked) return@setOnClickListener
                history.record(clients.size)
                confirmation.dismiss()
                review(clients, bodies, 0)
            }
        }
    }

    private fun review(clients: List<Recipient>, bodies: List<String>, index: Int) {
        if (index >= clients.size) { toast("Koniec listy szkiców. Status wysłania nieznany."); return }
        val client = clients[index]
        val body = bodies[index]
        val size = SmsSizing.calculate(body)
        show(AlertDialog.Builder(this).setTitle("Szkic ${index + 1}/${clients.size}: ${client.name}")
            .setMessage("${client.phone}\n\n$body\n\n${size.encoding}: ${size.units} jednostek, szac. ${size.segments} SMS. Koszt wg operatora.")
            .setNegativeButton("Zakończ", null)
            .setNeutralButton("Pomiń") { _, _ -> review(clients, bodies, index + 1) }
            .setPositiveButton("Otwórz edytor") { _, _ ->
                try {
                    startActivity(SmsIntents.editor(client, body))
                    show(AlertDialog.Builder(this).setTitle("Edytor otwarty — wysłanie nieznane")
                        .setMessage("Po powrocie możesz przejrzeć kolejny szkic. Aplikacja nie potwierdza wysłania ani doręczenia.")
                        .setPositiveButton("Następny") { _, _ -> review(clients, bodies, index + 1) }
                        .setNegativeButton("Zakończ", null))
                } catch (_: Exception) { toast("Nie można otworzyć aplikacji SMS") }
            })
    }

    @Deprecated("Platform activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == importCode) data?.data?.let { importCsvData(it) }
        if (requestCode == speechCode) {
            val dictated = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            val combined = listOf(session.body.trim(), dictated).filter { it.isNotBlank() }.joinToString(" ")
            if (combined.length > MAX_MESSAGE) toast("Dyktowanie przekracza limit 4000 znaków")
            else message.setText(combined)
        }
    }

    override fun onDestroy() {
        operation++
        dialog?.dismiss(); dialog = null
        work?.cancel(true); executor.shutdownNow()
        super.onDestroy()
    }
    private fun toast(body: String) = Toast.makeText(this, body, Toast.LENGTH_LONG).show()
}
