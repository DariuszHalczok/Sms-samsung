package pl.finrev.smssamsung

import android.content.Intent
import android.net.Uri

object SmsIntents {
    fun editor(recipient: Recipient, body: String): Intent {
        val phone = Phones.normalize(recipient.phone)
        require(body.isNotBlank() && body.length <= MAX_MESSAGE)
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null))
            .putExtra("sms_body", body)
    }
}
