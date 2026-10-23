package pl.finrev.smssamsung

enum class CommunicationMode(val label: String) {
    PRIVATE("Komunikacja prywatna"), REQUESTED("Materiał zamówiony"), MARKETING("Marketing — zablokowany")
}

object DraftPolicy {
    fun requireAllowed(mode: CommunicationMode?, requested: Boolean, material: Material?) {
        require(mode != null) { "Wybierz cel komunikacji" }
        require(mode != CommunicationMode.MARKETING) {
            "Marketing zablokowany: brak integracji weryfikującej uprzednią zgodę na cel i kanał SMS"
        }
        if (mode == CommunicationMode.REQUESTED) require(requested && material != null) {
            "Wybierz zamówiony materiał i potwierdź wcześniejszą prośbę tego odbiorcy"
        }
        if (mode == CommunicationMode.PRIVATE) require(material == null) {
            "Materiały udostępniaj w granicach wcześniejszej prośby odbiorcy"
        }
    }
}

object PublicProfile {
    const val PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
    fun validate(id: String): String {
        require(id.matches(Regex(PATTERN))) { "Wymagany publiczny identyfikator profilu UUID v4 z Doradca.pro" }
        return id
    }
}

enum class Material(val slug: String, val title: String) {
    MORTGAGE("hipoteka", "Kredyty hipoteczne"), REFINANCE("refinansowanie", "Refinansowanie kredytów"),
    FAMILY("ochrona-rodziny", "Ochrona rodziny"), RETIREMENT("emerytura", "Oszczędzanie na emeryturę"),
    IKZE("ikze", "IKZE"), BUSINESS("firma", "Finanse przedsiębiorców");
    fun url(profile: String) = "https://doradca.pro/edukacja/${PublicProfile.validate(profile)}/$slug"
}
