package pl.finrev.smssamsung

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout

/** Screen orchestration only; all validation and status changes live in the domain model. */
class WarmMarketUi(
    private val activity: Activity,
    private val session: DraftSession,
    private val show: (AlertDialog.Builder) -> AlertDialog?,
    private val toast: (String) -> Unit,
    private val runRequest: (() -> Result<WarmCampaign>, (Result<WarmCampaign>) -> Unit) -> Unit,
    private val onLoaded: (WarmCampaign) -> Unit
) {
    fun initialize() {
        val contacts = session.batch.all().map { Recipient(it.phone, it.name) }
        if (contacts.isEmpty() || contacts.any { it.name.isBlank() }) {
            toast("Wybierz od 1 do 30 znajomych z imieniem i poprawnym numerem"); return
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 8)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val token = EditText(activity).apply {
            hint = "Token doradcy — tylko pamięć sesji"; isSaveEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(8192))
        }
        val legal = CheckBox(activity).apply {
            text = "Mam uprawnienie i podstawę do przekazania tych kontaktów Doradca.pro oraz podstawę do planowanego SMS. Sama znajomość ani przyszła zgoda na stronie ich nie zastępuje."
        }
        layout.addView(token); layout.addView(legal)
        val popup = show(AlertDialog.Builder(activity).setTitle("Ciepły Rynek — ${contacts.size} kontaktów")
            .setMessage("Pilotaż: produkcyjne wdrożenie i Landing Page nie są potwierdzone. Po zatwierdzeniu imiona i numery trafią szyfrowanym połączeniem do izolowanej poczekalni Doradca.pro. Nie powstaną jeszcze klienci CRM. Nic nie jest wysyłane SMS-em.")
            .setView(layout).setNegativeButton("Anuluj", null).setPositiveButton("Przygotuj paczkę", null)
            .setOnDismissListener { token.text.clear() }) ?: return
        popup.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            legal.setOnCheckedChangeListener { _, checked -> isEnabled = checked }
            setOnClickListener {
                val secret = token.text.toString()
                if (!legal.isChecked || secret.length !in 20..8192 || secret.any { it.code !in 33..126 }) {
                    token.error = "Wymagany poprawny token doradcy"; return@setOnClickListener
                }
                val key = session.requestId(contacts)
                popup.dismiss()
                runRequest({ runCatching { WarmMarketApi().initialize(contacts, secret, key) } }) { result ->
                    result.fold(onSuccess = { campaign ->
                        show(AlertDialog.Builder(activity).setTitle("Paczka przygotowana na serwerze")
                            .setMessage("${campaign.recipients.size} indywidualnych linków, ważnych do ${campaign.expiresAt}. Wczytać szkice? To nadal nie jest wysyłka SMS ani zgoda na profilowanie klienta.")
                            .setNegativeButton("Anuluj", null).setPositiveButton("Wczytaj kolejkę") { _, _ ->
                                try {
                                    WarmRules.validateCampaign(campaign, contacts, java.time.Instant.now())
                                    onLoaded(campaign)
                                    overview()
                                } catch (_: Exception) { toast("Paczka jest niepoprawna lub wygasła") }
                            })
                    }, onFailure = { failure ->
                        toast(when ((failure as? WarmFailure)?.kind) {
                            WarmError.AUTHORIZATION -> "Token jest niepoprawny, wygasł lub został odwołany"
                            WarmError.FORBIDDEN -> "Doradca nie ma uprawnienia do Ciepłego Rynku"
                            WarmError.CONFLICT -> "Kontakt jest już w aktywnej paczce lub żądanie jest sprzeczne. Sprawdź Doradca.pro."
                            WarmError.RATE_LIMIT -> "Osiągnięto limit kampanii. Sprawdź Doradca.pro."
                            WarmError.NETWORK -> "Brak połączenia. Ponowienie dla tej samej listy użyje tego samego identyfikatora."
                            else -> "API nie jest dostępne albo zwróciło niepoprawną paczkę. Integracja nadal wymaga wdrożenia."
                        })
                    })
                }
            }
        }
    }

    fun overview() {
        val queue = session.warmQueue ?: return
        try { queue.ensureActive(java.time.Instant.now()) }
        catch (_: WarmFailure) { toast("Paczka wygasła. Przygotuj nową w Doradca.pro."); return }
        val rows = queue.campaign.recipients.mapIndexed { index, client ->
            val status = when (queue.state(index)) {
                WarmDraftState.DRAFT -> "szkic"
                WarmDraftState.EDITOR_OPENED -> "edytor otwarty — wysłanie nieznane"
                WarmDraftState.SKIPPED -> "pominięty"
            }
            "${index + 1}. ${client.name} — $status"
        }
        show(AlertDialog.Builder(activity).setTitle("Ciepły Rynek — kolejka ${rows.size}")
            .setItems(rows.toTypedArray()) { _, i -> review(i) }
            .setNeutralButton("Pierwszy oczekujący") { _, _ ->
                queue.nextPending()?.let { review(it) } ?: toast("Brak oczekujących szkiców")
            }.setNegativeButton("Zamknij", null))
    }

    private fun review(index: Int, repeatConfirmed: Boolean = false) {
        val queue = session.warmQueue ?: return
        try { queue.ensureActive(java.time.Instant.now()) }
        catch (_: WarmFailure) { toast("Paczka wygasła"); return }
        val client = queue.campaign.recipients[index]
        val editor = EditText(activity).apply {
            setText(queue.buffer(index)); minLines = 4; isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(MAX_MESSAGE))
        }
        fun description(text: String): String {
            val size = AndroidSmsSizing.calculate(text)
            return "${client.phone}\n${size.encoding}: ${size.units} jednostek, szac. ${size.segments} SMS. Koszt wg operatora. Zachowaj indywidualny link."
        }
        var activePopup: AlertDialog? = null
        editor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { queue.rememberEdit(index, s.toString()); activePopup?.setMessage(description(s.toString())) }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        val popup = show(AlertDialog.Builder(activity).setTitle("Szkic ${index + 1}/${queue.campaign.recipients.size}: ${client.name}")
            .setMessage(description(queue.buffer(index)))
            .setView(editor).setNegativeButton("Wróć") { _, _ -> overview() }
            .setNeutralButton("Pomiń") { _, _ -> queue.skip(index); overview() }
            .setPositiveButton("Otwórz edytor", null)) ?: return
        activePopup = popup
        popup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { queue.edit(index, editor.text.toString(), java.time.Instant.now()) }
            catch (_: Exception) { editor.error = "Paczka wygasła, treść jest za długa albo zmieniono indywidualny link"; return@setOnClickListener }
            if (queue.state(index) == WarmDraftState.EDITOR_OPENED && !repeatConfirmed) {
                popup.dismiss()
                show(AlertDialog.Builder(activity).setTitle("Edytor był już otwierany")
                    .setMessage("Wysłanie jest nieznane. Ponowne otwarcie może spowodować powtórny kontakt. Czy świadomie wrócić do tego szkicu?")
                    .setNegativeButton("Anuluj") { _, _ -> overview() }
                    .setPositiveButton("Przejrzyj ponownie") { _, _ -> review(index, true) })
                return@setOnClickListener
            }
            try {
                val openedAt = java.time.Instant.now()
                queue.ensureActive(openedAt)
                activity.startActivity(SmsIntents.editor(client, queue.body(index)))
                queue.opened(index, openedAt, repeatConfirmed)
                popup.dismiss()
                show(AlertDialog.Builder(activity).setTitle("Edytor otwarty — wysłanie nieznane")
                    .setMessage("Po powrocie sprawdź kolejny szkic. Nie potwierdzamy wysłania, doręczenia ani odczytu.")
                    .setPositiveButton("Wróć do kolejki") { _, _ -> overview() }
                    .setNegativeButton("Zakończ", null))
            } catch (_: Exception) { toast("Nie można otworzyć edytora lub paczka wygasła") }
        }
    }

}
