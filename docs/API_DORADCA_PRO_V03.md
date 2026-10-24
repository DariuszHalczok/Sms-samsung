> Dokument historyczny (0.3–0.5). Wymagania sprzeczne z Compliance & Security 1.0 z 23.10.2026 są wycofane. Aktualny zakres: [audyt](COMPLIANCE_AUDIT_2026-10-23.md) i [README](../README.md). Nie wdrażać poczekalni, linków per odbiorca, synchronizacji kontaktów ani dawnych kolejek.

# Historyczny projekt v0.3 — wyłączony w 0.4.0

Poniższy opis dotyczy prototypu, nie działającej integracji. W 0.4.0 usunięto formularz tokenu, klienta GET oraz stary deep link z tekstem. Nie potwierdzono serwera ani autoryzacji. Nie należy implementować tego projektu bez uzgodnienia z właścicielem rzeczywistego backendu. Aktualny stan: [audyt P0](P0_AUDIT.md).

---

# Integracja SMS Samsung z Doradca.pro — kontrakt v0.3

## Status
Aplikacja przyjmuje kampanię przez **GET HTTPS** na dokładnym hoście `doradca.pro`, z nagłówkiem `Authorization: Bearer <token>`. Użytkownik ręcznie wprowadza pełny adres istniejącego endpointu i token sesyjny. Token **nie jest zapisywany**. Nie ma automatycznego OAuth/parowania. Nie implementowano endpointu po stronie Doradca.pro.

Przykładowa odpowiedź **projektowanego** endpointu (nie twierdzenie, że już istnieje):

```json
{
  "message_template": "Dzień dobry {imie}, zapraszam do rozmowy. {link}",
  "recipients": [
    {
      "phone": "+48500111222",
      "name": "Jan Kowalski",
      "click_url": "https://doradca.pro/track/przykladowy-jednorazowy-identyfikator"
    }
  ]
}
```

API: max 30 rekordów, max 128 KiB, bez przekierowań HTTP, wyłącznie HTTPS dla `click_url`. Tekst max 4000 znaków. Link jest wstawiany tylko, jeśli szablon zawiera `{link}`. Jeżeli szablon wymaga linku, a klient go nie posiada, przygotowanie paczki jest blokowane.

## Personalizacja
Wspierane pola: `{imie}`/`{imię}`, `{nazwisko}`, `{link}`. Personalizacja wykonuje się lokalnie, osobno dla klienta; zawsze sprawdzaj dane przed otwarciem edytora SMS.

## Historia
Aplikacja zapisuje wyłącznie datę utworzenia paczki i liczbę odbiorców, do 30 pozycji. Nie zapisuje nazwisk, numerów, treści, linków ani tokenów. Historia **nie jest** dowodem wysłania/dostarczenia.

## Linki i statystyki
Zwykły SMS nie dostarcza danych o otwarciach. Doradca.pro musi sam generować indywidualne linki śledzące i na swojej infrastrukturze zliczać kliknięcia z odpowiednimi zasadami prywatności, retencji i informowania odbiorców. Android nie mierzy kliknięć w cudzej przeglądarce. Statystyki w Doradca.pro i dashboard wymagają implementacji backendu; aktualne APK jedynie wstawia linki do wiadomości.

## Bezpieczeństwo i produkcja
- Przy pobieraniu kampanii serwer musi autoryzować użytkownika, segment, doradcę i zgody na kontakt.
- Dedykowany OAuth PKCE/parowanie urządzenia i krótkotrwały token zamiast ręcznego tokenu — kolejne wdrożenie.
- Nigdy nie dodawaj sekretów do GitHub ani linków otwierających aplikację.
- Rzeczywiste SMS-y wymagają osobnego potwierdzenia w systemowej aplikacji. **Żadne SMS-y nie były wysyłane podczas developmentu**.
- Przed produkcją potrzebne testy urządzeniowe, testy odwołania uprawnień, negatywne scenariusze API i przegląd RODO.
