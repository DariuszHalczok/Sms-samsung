# Audyt przed zmianami — specyfikacja Compliance & Security 1.0

Audyt wykonano przed zmianami 0.6.0. Sprawdzone: Android `487d9a1` (pilot 0.5.0, PR #2), baza P0 `a08135c` (PR #1), prywatny backend `ccdb58a` (PR #1). Dokument przekazania i wcześniejszy projekt Ciepłego Rynku ustępują nowej specyfikacji z 23.10.2026. Audyt nie jest opinią prawną ani testem całej infrastruktury Doradca.pro.

| Priorytet | Rozbieżność w audytowanym kodzie | Wymagana zmiana |
|---|---|---|
| P0 | `WarmMarketApi.initialize` serializuje nazwy/numery do POST; `Campaigns` i SQL tworzą poczekalnię oraz linki per odbiorca | Usunąć cały przepływ, niezależnie od szyfrowania i ręcznego potwierdzenia |
| P0 | Manifest `READ_CONTACTS`, `ContactsSource.people/groups/groupMembers` skanuje książkę i grupy | Systemowy picker jednego numeru, wyłącznie czasowy odczyt wskazanego wiersza |
| P0 | `Recipient.clickUrl`, `/c/{code}` i statusy per odbiorca | Publiczny profil doradcy i materiał; brak identyfikatora odbiorcy przed zgłoszeniem |
| P0 | Checkboxy deklaracji w `MainActivity.preview` i `WarmMarketUi` nie są dowodem wcześniejszej zgody; brak trybów | Jawne tryby, marketing zamknięty bez weryfikowalnej integracji zgód |
| P1 | Szablony produktowe i sekwencyjne `review/next`, `WarmQueue` | Usunąć propozycje niezamówionego marketingu i kolejkę; wybór każdego szkicu przez użytkownika |
| P1 | `RecognizerIntent` korzysta z zewnętrznej usługi bez gwarancji przetwarzania lokalnego | Usunąć dyktowanie z aplikacji; brak ukrytej integracji AI |
| P1 | Brak publicznego katalogu, anonimowego kalkulatora, formularza z odrębnym marketingiem, historii/wycofania zgód | Osobny nowy moduł, kontakt dopiero z aktywnego formularza odbiorcy |
| P2 | Stare plany analityki kliknięć per klient, profilowania i follow-upów | Wycofać; ewentualne agregaty dopiero po analizie prywatności; bez analityki odbiorców |
| Produkcja | Brak rzeczywistego adaptera kont, zatwierdzonych administratorów/zgód, opinii PKE/RODO, odbioru i urządzenia Samsung | Nie deklarować gotowości produkcyjnej |

Zgodne elementy istniejące: osobny `ACTION_SENDTO smsto:`; brak SEND_SMS/READ_SMS, `SmsManager` wysyłającego i potwierdzeń doręczenia; bounded CSV/normalizacja/max30; RAM ViewModel bez SavedState; czyszczenie sesji; TLS/bez przekierowań; FLAG_SECURE i ograniczenie autofill. Limit i ręczna wysyłka nie są podstawą prawną kontaktu.

## Zakres po audycie

0.6.0 usuwa naruszenia przepływu danych i otwiera tylko GET publicznego katalogu. Marketing pozostaje zablokowany także po lokalnym oświadczeniu. Prywatny cel i wcześniejsza prośba są deklarowane przez doradcę dla jednej wiadomości; aplikacja nie potrafi automatycznie ustalić prawnego celu dowolnego tekstu. Nie należy przedstawiać tej deklaracji jako dowodu zgodności.

Nowe API katalogu i formularzy to implementacja w osobnym module backendu, **nie potwierdzony istniejący endpoint produkcyjny**. Nie wdrożono go na doradca.pro. Starsze PR i artefakty 0.4/0.5 nie są wersjami do wdrożenia zgodnie z nową specyfikacją. Historia Git przechowuje ich kod; nie przechowuje prawdziwych danych ani tokenów klientów.
