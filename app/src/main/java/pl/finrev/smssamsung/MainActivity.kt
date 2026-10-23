package pl.finrev.smssamsung

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity : ComponentActivity() {
    private val session: DraftSession by viewModels()
    private val profileStore by lazy { AdvisorProfileStore(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private var work: Future<*>? = null
    private var operation = 0L
    private var busy = false
    private var dialog: AlertDialog? = null
    private lateinit var message: EditText
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let(::readPickedContact)
    }
    private val csvPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::readCsv)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        getSharedPreferences("draft_history", 0).edit().clear().apply()
        setContentView(R.layout.activity_main)
        // Disable saving the entire view tree, including dynamic recipient labels.
        fun transient(view: View) {
            view.isSaveEnabled = false
            if (view is ViewGroup) for (i in 0 until view.childCount) transient(view.getChildAt(i))
        }
        transient(findViewById(R.id.composer_scroll))
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.composer_scroll)) { view, insets ->
            val edges = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom)
            insets
        }
        message = findViewById(R.id.message)
        message.setText(session.body)
        message.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                session.body = s.toString(); session.changed(); updateFeedback()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        action(R.id.choose_mode, ::chooseMode)
        action(R.id.pick_contact, ::pickContact)
        action(R.id.choose_recipient) { chooseRecipient(false) }
        action(R.id.remove_recipient) { session.removeSelected(); render() }
        action(R.id.catalog, ::chooseCatalog)
        action(R.id.replies, ::chooseReply)
        action(R.id.preview) { chooseRecipient(true) }
        action(R.id.clear_session, ::clearSession)
        action(R.id.more, ::more)
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        findViewById<TextView>(R.id.version_footer).text = getString(R.string.footer, version)
        acceptInternalDraft(); render(); updateFeedback()
    }

    private fun action(id: Int, action: () -> Unit) { findViewById<View>(id).setOnClickListener { action() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); acceptInternalDraft() }
    private fun acceptInternalDraft() {
        val body = SharedDraft.take() ?: return
        clearSession(); message.setText(body) // Shared text cannot supply recipients, a mode or a profile.
    }
    private fun show(builder: AlertDialog.Builder): AlertDialog? {
        dialog?.dismiss()
        if (isFinishing || isDestroyed) return null
        return builder.create().also { dialog = it; it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE); it.show() }
    }
    private fun clearSession() {
        operation++; work?.cancel(true); session.clear(); message.text.clear(); SharedDraft.clear()
        dialog?.dismiss(); dialog = null; busy = false
        findViewById<View>(R.id.loading).visibility = View.GONE
        findViewById<View>(R.id.load_status).visibility = View.GONE
        render()
    }
    private fun updateFeedback() {
        val size = AndroidSmsSizing.calculate(session.body)
        findViewById<TextView>(R.id.message_feedback).text = getString(R.string.body_feedback, session.body.length, size.segments)
    }
    private fun render() {
        val list = session.batch.all(); val selected = session.selected()
        findViewById<TextView>(R.id.recipients).text = when {
            selected != null -> getString(R.string.recipient_selected, selected.name.ifBlank { getString(R.string.contact_without_name) }, selected.phone)
            list.isNotEmpty() -> getString(R.string.recipient_pending)
            else -> getString(R.string.recipient_empty)
        }
        findViewById<TextView>(R.id.recipient_count).text = getString(R.string.recipient_count, list.size)
        findViewById<View>(R.id.choose_recipient).isEnabled = list.isNotEmpty()
        findViewById<View>(R.id.remove_recipient).visibility = if (selected == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.mode).text = session.mode?.label ?: getString(R.string.mode_empty)
        findViewById<View>(R.id.mode_hint).visibility = if (session.mode == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.mode_hint).setText(when (session.mode) {
            CommunicationMode.PRIVATE -> R.string.mode_private_hint
            CommunicationMode.REQUESTED -> R.string.mode_requested_hint
            CommunicationMode.MARKETING -> R.string.mode_marketing_hint
            null -> R.string.mode_empty
        })
        findViewById<View>(R.id.material_section).visibility = if (session.mode == CommunicationMode.REQUESTED) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.material).text = session.material?.title ?: getString(R.string.material_empty)
        findViewById<TextView>(R.id.profile_status).setText(if (profileStore.load() == null) R.string.profile_missing else R.string.profile_configured)
        findViewById<View>(R.id.catalog).isEnabled = !busy
        findViewById<View>(R.id.replies).isEnabled = session.material != null
    }
    private fun chooseMode() {
        show(AlertDialog.Builder(this).setTitle(R.string.mode_dialog)
            .setItems(CommunicationMode.entries.map { it.label }.toTypedArray()) { _, index ->
                val chosen = CommunicationMode.entries[index]
                fun apply() {
                    session.changeMode(chosen); message.setText(session.body); render()
                    if (chosen == CommunicationMode.MARKETING) toast(R.string.mode_marketing_hint)
                }
                if (session.mode != null && chosen != session.mode && (session.body.isNotBlank() || session.material != null)) {
                    show(AlertDialog.Builder(this).setTitle(R.string.mode_change_title).setMessage(R.string.mode_change_body)
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.mode_change_confirm) { _, _ -> apply() })
                } else apply()
            })
    }
    private fun pickContact() {
        try { contactPicker.launch(ContactPicker.intent()) }
        catch (_: Exception) { toast(R.string.picker_missing) }
    }
    private fun more() {
        val labels = intArrayOf(R.string.profile_title, R.string.import_csv, R.string.privacy_title)
        show(AlertDialog.Builder(this).setTitle(R.string.more).setItems(labels.map { getString(it) }.toTypedArray()) { _, index ->
            when (index) {
                0 -> configureProfile(false)
                1 -> importCsv()
                2 -> show(AlertDialog.Builder(this).setTitle(R.string.privacy_title).setMessage(R.string.privacy_body).setPositiveButton(R.string.close, null))
            }
        })
    }
    private fun configureProfile(loadAfterSave: Boolean) {
        val current = profileStore.load()
        val field = EditText(this).apply {
            id = R.id.profile_input; hint = getString(R.string.profile_hint); isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(36)); setText(current.orEmpty())
        }
        fun discardBoundMaterial() {
            operation++; work?.cancel(true); busy = false
            findViewById<View>(R.id.loading).visibility = View.GONE
            findViewById<View>(R.id.load_status).visibility = View.GONE
            if (session.material != null) message.text.clear()
            session.clearMaterial(); render()
        }
        val builder = AlertDialog.Builder(this).setTitle(R.string.profile_title)
            .setMessage(getString(R.string.profile_explanation) + "\n\n" + getString(R.string.profile_change_warning))
            .setView(field).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (loadAfterSave) R.string.profile_save_load else R.string.profile_save, null)
        if (current != null) builder.setNeutralButton(R.string.profile_remove) { _, _ ->
            profileStore.clear(); field.text.clear(); discardBoundMaterial()
        }
        val confirmation = show(builder) ?: return
        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val id = field.text.toString().trim()
            try { PublicProfile.validate(id) } catch (_: Exception) { field.error = getString(R.string.profile_invalid); return@setOnClickListener }
            profileStore.save(id)
            if (current != id) discardBoundMaterial()
            field.text.clear(); confirmation.dismiss(); render()
            if (loadAfterSave) chooseCatalog() else toast(R.string.profile_saved)
        }
    }
    private fun chooseCatalog() {
        if (session.mode != CommunicationMode.REQUESTED) { toast(R.string.catalog_requested_only); return }
        val id = profileStore.load()
        if (id == null) { configureProfile(true); return }
        load({ MaterialCatalog().load(id) }, R.string.catalog_loading, R.string.catalog_unavailable) { materials ->
            show(AlertDialog.Builder(this).setTitle(R.string.catalog_pick)
                .setItems(materials.map { it.title }.toTypedArray()) { _, index ->
                    session.publicProfile = id; session.material = materials[index]; session.changed()
                    if (session.body.isBlank()) message.setText(RequestedReply.SIMPLE.forDraft(session.mode, session.material))
                    render()
                })
        }
    }
    private fun chooseReply() {
        if (session.mode != CommunicationMode.REQUESTED || session.material == null) { toast(R.string.replies_missing); return }
        show(AlertDialog.Builder(this).setTitle(R.string.replies_title)
            .setItems(RequestedReply.entries.map { getString(it.labelRes) }.toTypedArray()) { _, index ->
                val text = RequestedReply.entries[index].forDraft(session.mode, session.material)
                if (session.body.isBlank() || session.body == text) message.setText(text)
                else show(AlertDialog.Builder(this).setTitle(R.string.replace_body_title).setMessage(R.string.replace_body_explanation)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.replace_body) { _, _ -> message.setText(text) })
            })
    }
    private fun <T> load(read: () -> T, loading: Int = R.string.data_loading, failure: Int = R.string.data_unavailable, done: (T) -> Unit) {
        val revision = session.revision; val request = ++operation
        work?.cancel(true); busy = true; render()
        findViewById<View>(R.id.loading).visibility = View.VISIBLE
        findViewById<TextView>(R.id.load_status).apply { visibility = View.VISIBLE; setText(loading) }
        work = executor.submit {
            val result = runCatching { read() }
            runOnUiThread {
                if (isDestroyed || isFinishing || operation != request) return@runOnUiThread
                busy = false; findViewById<View>(R.id.loading).visibility = View.GONE; render()
                val status = findViewById<TextView>(R.id.load_status)
                when {
                    session.revision != revision -> status.setText(R.string.stale_result)
                    result.isFailure -> status.setText(failure)
                    else -> { status.visibility = View.GONE; done(result.getOrThrow()) }
                }
            }
        }
    }
    private fun chooseRecipient(forPreview: Boolean) {
        if (forPreview && (session.mode == null || session.mode == CommunicationMode.MARKETING)) {
            toast(if (session.mode == null) R.string.mode_empty else R.string.mode_marketing_hint); return
        }
        if (forPreview && session.body.isBlank()) { message.error = getString(R.string.body_required); return }
        val list = session.batch.all()
        if (list.isEmpty()) { toast(R.string.no_recipient); return }
        if (forPreview) session.selected()?.let { preview(it); return }
        // Explicit selection. No next-item state or automatic advance.
        show(AlertDialog.Builder(this).setTitle(R.string.recipient_dialog)
            .setItems(list.map { "${it.name} — ${it.phone}" }.toTypedArray()) { _, i ->
                session.select(list[i].phone); render()
                if (forPreview) preview(list[i])
            })
    }
    private fun preview(recipient: Recipient) {
        val mode = session.mode
        if (mode == CommunicationMode.REQUESTED && session.material == null) { toast(R.string.replies_missing); return }
        val body = try { Templates.render(session.body, recipient, session.material?.url(session.publicProfile).orEmpty()) }
        catch (e: IllegalArgumentException) { message.error = e.message; return }
        val revision = session.revision
        val size = AndroidSmsSizing.calculate(body)
        val confirmation = CheckBox(this).apply {
            setText(if (mode == CommunicationMode.REQUESTED) R.string.confirm_requested else R.string.confirm_private)
            isSaveEnabled = false
        }
        val review = show(AlertDialog.Builder(this).setTitle(R.string.review_title)
            .setMessage(getString(R.string.review_body, recipient.name, recipient.phone, body, size.encoding, size.segments))
            .setView(confirmation).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.open_editor, null)) ?: return
        review.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            confirmation.setOnCheckedChangeListener { _, checked -> isEnabled = checked }
            setOnClickListener {
                try {
                    require(confirmation.isChecked && revision == session.revision)
                    DraftPolicy.requireAllowed(mode, mode == CommunicationMode.REQUESTED && confirmation.isChecked, session.material)
                    startActivity(SmsIntents.editor(recipient, body))
                    review.dismiss(); toast(R.string.editor_opened)
                } catch (_: Exception) { toast(R.string.editor_unavailable) }
            }
        }
    }
    private fun importCsv() {
        try { csvPicker.launch(arrayOf("text/csv", "text/plain", "application/csv")) }
        catch (_: Exception) { toast(R.string.import_missing) }
    }
    private fun readPickedContact(uri: Uri) {
        load({ ContactPicker.read(contentResolver, uri) }) { recipient ->
            try { session.addPicked(recipient); render() } catch (_: IllegalArgumentException) { toast(R.string.invalid_recipients) }
        }
    }
    private fun readCsv(uri: Uri) {
        load({ contentResolver.openInputStream(uri)?.use { CsvImport.read(it) } ?: error("unavailable") }) { imported ->
            show(AlertDialog.Builder(this).setTitle(resources.getQuantityString(R.plurals.import_title, imported.size, imported.size)).setMessage(R.string.import_explanation)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.replace_contacts) { _, _ ->
                    try { session.replaceRecipients(imported); render() } catch (_: IllegalArgumentException) { toast(R.string.invalid_recipients) }
                })
        }
    }
    override fun onDestroy() {
        operation++; dialog?.dismiss(); dialog = null; work?.cancel(true); executor.shutdownNow(); super.onDestroy()
    }
    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()
}
