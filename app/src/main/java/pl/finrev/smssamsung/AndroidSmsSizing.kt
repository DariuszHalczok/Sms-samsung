package pl.finrev.smssamsung

/** Platform segmentation handles national alphabets and Unicode boundaries; never dispatches SMS. */
object AndroidSmsSizing {
    fun calculate(body: String): SmsSize = try {
        val parts = android.telephony.SmsMessage.calculateLength(body, false)
        SmsSize(if (parts[3] == 1) "GSM-7" else "Unicode", parts[1], parts[0])
    } catch (_: Exception) { SmsSizing.calculate(body) }
}
