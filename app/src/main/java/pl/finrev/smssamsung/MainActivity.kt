package pl.finrev.smssamsung

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private val executor = Executors.newSingleThreadExecutor()
    private var work: Future<*>? = null
    private var operation = 0L
    private var dialog: AlertDialog? = null
    private lateinit var recipients: TextView
    private lateinit var modeLabel: TextView
    private lateinit var materialLabel: TextView
    private lateinit var message: EditText
    private val pickerCode = 101
    private val importCode = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Remove aggregate history left by older versions; no replacement persistent store.
        getSharedPreferences("draft_history", 0).edit().clear().apply()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        fun button(label: String, viewId: Int? = null, action: () -> Unit) = Button(this).apply {
            text = label; viewId?.let { id = it }; setOnClickListener { action() }; layout.addView(this)
        }
        layout.addView(TextView(this).apply { text = "Relationship Engine 0.6"; textSize = 24f })
        layout.addView(TextView(this).apply {
            text = "Kontakty i szkice pozostają w pamięci telefonu. Ręczne otwarcie edytora nie potwierdza wysłania ani legalności kontaktu. System SMS, Android i operator mają własne zasady przetwarzania."
        })
        modeLabel = TextView(this).apply { id = R.id.mode; isSaveEnabled = false }
        layout.addView(modeLabel)
        button("Wybierz cel komunikacji", R.id.choose_mode) { chooseMode() }
        button("Wybierz jeden kontakt w systemie", R.id.pick_contact) {
            try { startActivityForResult(ContactPicker.intent(), pickerCode) }
            catch (_: Exception) { toast("Brak systemowego selektora numerów telefonu") }
        }
        button("Import lokalnego CSV") {
            try {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "application/csv"))
                }, importCode)
            } catch (_: Exception) { toast("Nie można otworzyć pliku") }
        }
        recipients = TextView(this).apply { id = R.id.recipients; isSaveEnabled = false }
        layout.addView(recipients)
        materialLabel = TextView(this).apply { id = R.id.material; isSaveEnabled = false }
        layout.addView(materialLabel)
        button("Pobierz katalog zamówionych materiałów", R.id.catalog) { chooseCatalog() }
        message = EditText(this).apply {
            id = R.id.message; hint = "Treść SMS; opcjonalnie {imie}, {nazwisko}, {link}"; minLines = 4
            isSaveEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(MAX_MESSAGE)); setText(session.body)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    session.body = s.toString(); session.changed()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        layout.addView(message)
        button("Wybierz odbiorcę i sprawdź szkic", R.id.preview) { chooseRecipient() }
        button("Wyczyść sesję", R.id.clear_session) { clearSession() }
        button("Stan integracji Doradca.pro") {
            show(AlertDialog.Builder(this).setTitle("Integracja produkcyjna oczekuje")
                .setMessage("Katalog wymaga wdrożenia nowego API i publicznego profilu. Nie zastępuj go dawnymi linkami /c/. Marketing jest zablokowany bez weryfikacji uprzedniej zgody. Aplikacja nie wysyła danych kontaktów ani treści do API lub modeli AI.")
                .setPositiveButton("OK", null))
        }
        setContentView(ScrollView(this).apply {
            isSaveEnabled = false; addView(layout)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom); insets
            }
        })
        acceptInternalDraft(); render()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); acceptInternalDraft() }
    private fun acceptInternalDraft() {
        val body = SharedDraft.take() ?: return
        clearSession(); message.setText(body) // Shared text never supplies recipients or a communication mode.
    }
    private fun show(builder: AlertDialog.Builder): AlertDialog? {
        dialog?.dismiss()
        if (isFinishing || isDestroyed) return null
        return builder.create().also {
            dialog = it; it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE); it.show()
        }
    }
    private fun clearSession() {
        operation++; work?.cancel(true); session.clear(); message.text.clear(); SharedDraft.clear()
        dialog?.dismiss(); dialog = null; render()
    }
    private fun render() {
        val list = session.batch.all()
        recipients.text = if (list.isEmpty()) "Odbiorcy: brak" else
            "Wybrani lokalnie (${list.size}/30): " + list.joinToString(", ") { it.name.ifBlank { it.phone } }
        modeLabel.text = "Cel: " + (session.mode?.label ?: "wybierz przed przygotowaniem SMS")
        materialLabel.text = "Materiał: " + (session.material?.title ?: "brak")
    }
    private fun chooseMode() {
        show(AlertDialog.Builder(this).setTitle("Rzeczywisty cel komunikacji")
            .setItems(CommunicationMode.entries.map { it.label }.toTypedArray()) { _, index ->
                val chosen = CommunicationMode.entries[index]
                if (chosen != session.mode) {
                    session.mode = chosen; session.material = null; session.publicProfile = ""
                    message.text.clear(); session.changed(); render()
                }
                if (chosen == CommunicationMode.MARKETING) toast("Brak zweryfikowanej uprzedniej zgody — marketing zablokowany")
            })
    }
    private fun chooseCatalog() {
        if (session.mode != CommunicationMode.REQUESTED) {
            toast("Najpierw wybierz tryb Materiał zamówiony, zgodnie z wcześniejszą prośbą odbiorcy"); return
        }
        val profile = EditText(this).apply {
            hint = "Publiczny UUID profilu Doradca.pro"; isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(36)); setText(session.publicProfile)
        }
        show(AlertDialog.Builder(this).setTitle("Publiczny katalog")
            .setMessage("Żądanie zawiera tylko publiczny identyfikator doradcy. Nie wpisuj danych odbiorcy ani tokenu sesji.")
            .setView(profile).setNegativeButton("Anuluj", null).setPositiveButton("Pobierz") { _, _ ->
                val id = profile.text.toString(); profile.text.clear()
                try { PublicProfile.validate(id) } catch (_: Exception) { toast("Niepoprawny identyfikator profilu"); return@setPositiveButton }
                load({ MaterialCatalog().load(id) }) { materials ->
                    show(AlertDialog.Builder(this).setTitle("Wybierz zamówiony materiał")
                        .setItems(materials.map { it.title }.toTypedArray()) { _, index ->
                            session.publicProfile = id; session.material = materials[index]; session.changed()
                            message.setText("Zgodnie z Twoją prośbą przesyłam materiał: {link}"); render()
                        })
                }
            })
    }
    private fun <T> load(read: () -> T, done: (T) -> Unit) {
        val revision = session.revision; val request = ++operation
        work?.cancel(true)
        work = executor.submit {
            try {
                val result = read()
                runOnUiThread { if (!isDestroyed && !isFinishing && operation == request && session.revision == revision) done(result) }
            } catch (_: Exception) {
                runOnUiThread { if (!isDestroyed && !isFinishing && operation == request && session.revision == revision)
                    toast("Nie wczytano danych. Katalog może wymagać wdrożenia; sprawdź format i dostępność.") }
            }
        }
    }
    private fun updateRecipients(candidate: List<Recipient>) {
        try { session.batch.replace(candidate); session.changed(); render() }
        catch (_: Exception) { toast("Nie zmieniono listy: sprawdź dane, duplikaty i limit 30") }
    }
    private fun chooseRecipient() {
        if (session.mode == null || session.mode == CommunicationMode.MARKETING) {
            toast("Wybierz dopuszczalny cel. Marketing pozostaje zablokowany bez zweryfikowanej zgody."); return
        }
        val list = session.batch.all()
        if (list.isEmpty()) { toast("Wybierz kontakt"); return }
        // A manual chooser, with no next-item state, queue, scheduler or automatic advance.
        show(AlertDialog.Builder(this).setTitle("Samodzielnie wybierz jednego odbiorcę")
            .setItems(list.map { "${it.name} — ${it.phone}" }.toTypedArray()) { _, i -> preview(list[i]) })
    }
    private fun preview(recipient: Recipient) {
        val mode = session.mode
        val body = try {
            Templates.render(session.body, recipient, session.material?.url(session.publicProfile).orEmpty())
        } catch (e: IllegalArgumentException) { message.error = e.message; return }
        val size = AndroidSmsSizing.calculate(body)
        val confirmation = CheckBox(this).apply {
            text = if (mode == CommunicationMode.REQUESTED)
                "Ten odbiorca wcześniej poprosił o ten materiał; treść odpowiada zakresowi jego prośby."
            else "Treść ma rzeczywisty cel prywatny. Nie używam tego trybu do maskowania marketingu."
            isSaveEnabled = false
        }
        val review = show(AlertDialog.Builder(this).setTitle("Sprawdź jedną wiadomość")
            .setMessage("${recipient.name}\n${recipient.phone}\n\n$body\n\n${size.encoding}, szac. ${size.segments} SMS. Koszt wg operatora.\nTo oświadczenie nie jest weryfikacją podstawy prawnej. Ręczny SMS ani bezpłatny materiał same w sobie jej nie zapewniają.")
            .setView(confirmation).setNegativeButton("Anuluj", null).setPositiveButton("Otwórz edytor SMS", null)) ?: return
        review.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            confirmation.setOnCheckedChangeListener { _, checked -> isEnabled = checked }
            setOnClickListener {
                try {
                    require(confirmation.isChecked)
                    DraftPolicy.requireAllowed(mode, mode == CommunicationMode.REQUESTED && confirmation.isChecked, session.material)
                    startActivity(SmsIntents.editor(recipient, body))
                    review.dismiss() // No next recipient is selected or opened.
                    toast("Edytor otwarty. Wysłanie i doręczenie nieznane.")
                } catch (_: Exception) { toast("Nie otwarto edytora. Sprawdź cel, prośbę i dostępność aplikacji SMS.") }
            }
        }
    }
    @Deprecated("Platform activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        if (requestCode == pickerCode) {
            load({ ContactPicker.read(contentResolver, uri) }) { recipient ->
                updateRecipients(session.batch.all() + recipient)
            }
        }
        if (requestCode == importCode) {
            load({ contentResolver.openInputStream(uri)?.use { CsvImport.read(it) } ?: error("unavailable") }) { imported ->
                show(AlertDialog.Builder(this).setTitle("Import lokalny: ${imported.size} kontaktów")
                    .setMessage("Zastąpić lokalną listę? Import nie przenosi zgód i nie wysyła danych na serwer. Limit 30 jest ograniczeniem interfejsu, nie podstawą kontaktu.")
                    .setNegativeButton("Anuluj", null).setPositiveButton("Zastąp") { _, _ -> updateRecipients(imported) })
            }
        }
    }
    override fun onDestroy() {
        operation++; dialog?.dismiss(); dialog = null; work?.cancel(true); executor.shutdownNow(); super.onDestroy()
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
