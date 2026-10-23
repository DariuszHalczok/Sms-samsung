# Ciepły Rynek — pilot 0.5.0

Nowy moduł przygotowuje do 30 indywidualnych szkiców z imionami i linkami Doradca.pro. Telefon pozostaje terminalem: każdy SMS otwierany wyłącznie przez ACTION_SENDTO `smsto:` i zatwierdzany ręcznie. Nie ma SEND_SMS, czytania wiadomości ani statusu „wysłano”.

## Status

Backend inicjalizacji powstał jako osobny komponent w prywatnym repozytorium Doradca.pro. Jest testowany na PostgreSQL i lokalnym serwerze HTTP, z rzeczywistą weryfikacją testowych podpisów i odwołania sesji. **Nie wdrożono go na doradca.pro i nie wykonano autoryzacji na serwerze produkcyjnym.**

Specyfikacja użytkownika urwała się przy `Response:`. Kontrakt poniżej jest propozycją. Landing Page, zgody, wykorzystanie kodu i konwersja do leada wymagają dalszej specyfikacji i adaptera właściwego CRM. Wygenerowane linki `/c/{code}` nie mają jeszcze tutaj Landing Page; nie są gotowe do kontaktu z klientami.

## Android

- Min SDK 26, target SDK 35 zgodnie z nową specyfikacją, compile SDK 36; testy nadal na Androidzie 16/API 36.
- Pilot API dostępny tylko w debug APK, pod przyciskiem „Ciepły Rynek — pilotaż API”. Wymaga wybrania 1–30 kontaktów z nazwą i poprawnym numerem oraz świadomego zatwierdzenia przekazania ich do poczekalni. Nie wysyła całej książki adresowej.
- Przed POST wymagane potwierdzenie podstawy przekazania kontaktów i planowanego SMS. Sama znajomość, przyszłe kliknięcie i zgoda na Landing Page nie zastępują wymaganej podstawy wcześniejszego kontaktu. Checkbox nie jest sprawdzeniem prawnym ani serwerową weryfikacją zgód.
- Ręczny token doradcy tylko na czas żądania w RAM; pole password, brak zapisu do Bundle/autofill/klawiatury i wyczyszczenie po zamknięciu. To etap testowy, nie OAuth PKCE/parowanie.
- Stały POST `https://doradca.pro/api/warm-market/init`, bez przekierowań, własnych hostów/query i cache. Timeout, limit requestu 16 KiB i odpowiedzi 128 KiB, ścisły UTF-8 i walidacja pełnej paczki. INTERNET występuje wyłącznie w manifeście debug; wariant release nie ma pilota API ani tego uprawnienia.
- Idempotency-Key zachowany dla niezmienionej listy w tej samej sesji. Ręczne ponowienie po braku sieci nie tworzy kolejnej kampanii na serwerze. Klient nie ponawia POST automatycznie.
- Odpowiedź musi odpowiadać dokładnie wybranym numerom/nazwom, zawierać unikalne linki HTTPS na dokładnym hoście doradca.pro z opaque kodem, prawidłowy UUID kampanii i aktualny TTL do 7 dni. Błędy odrzucają całą paczkę.
- Kolejka pokazuje każdy szkic, umożliwia indywidualną edycję z zachowaniem własnego linku, pomijanie i powrót. Statusy: „szkic”, „edytor otwarty — wysłanie nieznane”, „pominięty”. Powtórne otwarcie wymaga dodatkowego potwierdzenia. Wygasła paczka blokuje otwarcie edytora.
- Edycje i kolejka przeżywają rotację tylko w ViewModel. Zakończenie procesu i „Wyczyść sesję” je usuwają. Nie zapisuje się klientów, kodów ani treści na dysku. Po zmianie odbiorców linki wcześniejszej kampanii nie są przenoszone do zwykłej paczki.

## Propozycja kontraktu

Request: JSON `{ "contacts": [{ "name": "Test Example", "phone": "+12025550101" }] }`; Bearer i UUID `Idempotency-Key` wyłącznie w nagłówkach. Przykład jest syntetyczny. Response 200 (również idempotentny retry):

```json
{
  "campaign_id": "UUID kampanii",
  "expires_at": "RFC3339 UTC",
  "message_template": "Cześć {imie}, … {link}",
  "recipients": [{"name": "Test Example", "phone": "+12025550101", "click_url": "https://doradca.pro/c/OPAQUE_43_ZNAKI"}]
}
```

Obsługiwane błędy: 401/403/409/429, błędne dane, niedostępność i brak sieci. Nie wyświetla się surowego response body ani sekretów. Odpowiedź backendu jest danymi do ręcznego przeglądu, nie potwierdzeniem wysłania.

## Do produkcji

Potrzebne: właściwy adapter auth/ACL i kont doradców, wdrożenie TLS i test autoryzacji na realnym serwerze, pełne reguły podstaw kontaktu/zgód/opt-out, Landing Page i konwersja, serwerowe statystyki z obsługą botów, retencja i usuwanie danych, wznowienie/anulowanie kampanii po utracie sesji telefonu, OAuth/parowanie, klucz produkcyjny i test fizycznego Galaxy S24 Ultra. Doradca.pro pozostaje jedynym CRM; przed zgodą nie tworzy się klientów ani profili finansowych. Odczytu zwykłego SMS nie można mierzyć.
