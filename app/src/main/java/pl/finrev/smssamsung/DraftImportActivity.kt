package pl.finrev.smssamsung

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager

/** Separate external entry point: only plain shared text, no recipients, links or credentials. */
class DraftImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val body = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain")
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() else null
        intent = Intent()
        if (body.isNullOrBlank() || body.length > MAX_MESSAGE) { finish(); return }
        AlertDialog.Builder(this).setTitle("Zaimportować udostępniony szkic?")
            .setMessage("Bieżąca paczka zostanie wyczyszczona. Odbiorców wybierzesz ponownie.\n\n$body")
            .setNegativeButton("Anuluj") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .setPositiveButton("Importuj szkic") { _, _ ->
                SharedDraft.put(body)
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            }.show().window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
