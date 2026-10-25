package pl.finrev.smssamsung

/** Responses only to an existing request. Selection never establishes a legal basis. */
enum class RequestedReply(val labelRes: Int, val template: String) {
    SIMPLE(R.string.reply_simple, "Zgodnie z Twoją prośbą przesyłam materiał: {link}"),
    PERSONAL(R.string.reply_personal, "Cześć {imie}, przesyłam materiał, o który prosisz: {link}"),
    EXPLANATION(R.string.reply_explanation, "Przesyłam materiał zgodnie z Twoją prośbą: {link}\nJeśli coś wymaga wyjaśnienia, daj znać.");

    fun forDraft(mode: CommunicationMode?, material: Material?): String {
        DraftPolicy.requireAllowed(mode, true, material)
        require(mode == CommunicationMode.REQUESTED)
        return template
    }
}
