package pl.finrev.smssamsung

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var numberField: EditText
    private lateinit var messageField: EditText
    private lateinit var contactLabel: TextView
    private val pickPhoneRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 40)
        }
        body.addView(TextView(this).apply {
            text = "SMS Samsung — Quick Start"
            textSize = 24f
        })
        body.addView(TextView(this).apply {
            text = "Nie wysyła automatycznie. Każdy SMS zatwierdzasz w aplikacji Wiadomości."
            textSize = 15f
        })
        contactLabel = TextView(this).apply { text = "Odbiorca: wybierz kontakt lub wpisz numer"; textSize = 16f }
        body.addView(contactLabel)
        numberField = EditText(this).apply {
            hint = "Numer telefonu, np. +48..."
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        body.addView(numberField)
        body.addView(Button(this).apply {
            text = "Wybierz numer z kontaktów"
            setOnClickListener { pickContact() }
        })
        messageField = EditText(this).apply {
            hint = "Treść SMS"
            minLines = 4
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        body.addView(messageField)
        body.addView(Button(this).apply {
            text = "Sprawdź i otwórz SMS"
            setOnClickListener { preview() }
        })
        body.addView(TextView(this).apply {
            text = "Możesz też udostępnić tekst z ChatGPT do aplikacji SMS Samsung."
        })
        setContentView(ScrollView(this).apply { addView(body) })
        readSharedText(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSharedText(intent)
    }

    private fun readSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            messageField.setText(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")
        }
    }

    private fun pickContact() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                pickPhoneRequest
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Nie znaleziono aplikacji kontaktów", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Uses Android contact picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickPhoneRequest || resultCode != RESULT_OK || data?.data == null) return
        try {
            val uri = data.data ?: return
            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val phone = cursor.getString(0) ?: ""
                    val name = cursor.getString(1) ?: "Wybrany kontakt"
                    numberField.setText(phone)
                    contactLabel.text = "Odbiorca: $name"
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Nie udało się odczytać numeru. Wpisz go ręcznie.", Toast.LENGTH_LONG).show()
        }
    }

    private fun preview() {
        val phone = numberField.text.toString().trim()
        val message = messageField.text.toString().trim()
        if (!phone.matches(Regex("^[+0-9() .-]{6,30}$")) || phone.count { it.isDigit() } < 6) {
            numberField.error = "Podaj poprawny numer telefonu"
            return
        }
        if (message.isBlank()) {
            messageField.error = "Wpisz wiadomość"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Sprawdź przed wysłaniem")
            .setMessage("Numer: $phone\n\nTreść:\n$message\n\nAplikacja otworzy edytor SMS, ale niczego nie wyśle.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Otwórz Wiadomości") { _, _ -> openSms(phone, message) }
            .show()
    }

    private fun openSms(phone: String, message: String) {
        val normalized = phone.filter { it.isDigit() || it == '+' }
        val send = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", normalized, null)).apply {
            putExtra("sms_body", message)
        }
        try {
            startActivity(send)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Brak aplikacji SMS na telefonie", Toast.LENGTH_LONG).show()
        }
    }
}
