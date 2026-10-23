# SMS Samsung — Asystent SMS (beta 0.2)

Aplikacja Android 16 / Galaxy S24 Ultra. Działa na danych kontaktowych widocznych na telefonie (w tym zsynchronizowanych z kontem Google).

## Funkcje beta 0.2
- Wyszukuj kontakty po imieniu lub nazwisku i zaznaczaj wielu odbiorców.
- Wybieraj grupy kontaktów, o ile są dostępne przez systemowy Contacts Provider.
- Dyktuj **treść** wiadomości przez usługę rozpoznawania mowy Android.
- Udostępniaj tekst z aplikacji ChatGPT do SMS Samsung przez systemowe menu "Udostępnij", a następnie wybieraj odbiorców.
- Przed wysyłką wyświetlany jest podgląd odbiorców i wiadomości.
- Każdego SMS-a otwiera się indywidualnie w domyślnej aplikacji SMS; wysłanie wymaga ręcznego potwierdzenia. Aplikacja nie stwierdza, czy SMS rzeczywiście wysłano.

## Ograniczenia
- **Polecenie wypowiedziane w samej rozmowie ChatGPT nie uruchamia jeszcze aplikacji ani nie odczytuje jej kontaktów.** To wymaga oddzielnego, świadomie autoryzowanego mostu integracyjnego (np. backend + parowanie urządzenia i powiadomienie aplikacji albo obsługa linku w urządzeniu).
- Dyktowanie w aplikacji rozpoznaje treść, a nie polecenia rozpoznawania nazwisk.
- Wybór grup zależy od konta i sposobu synchronizacji; nie wszystkie etykiety Google Contacts są wystawiane jako systemowe grupy.
- Nie korzysta z SEND_SMS i nie wysyła automatycznie. Brak masowej wysyłki bezpośredniej.
- Wymaga uprawnienia READ_CONTACTS udzielonego na telefonie. Nie wysyła książki kontaktów na serwer.
- Numery różnych formatów mogą pojawić się podwójnie; przed wysyłką należy zweryfikować listę.

## Instalacja
Otwórz [Actions](https://github.com/DariuszHalczok/Sms-samsung/actions), wybierz najnowsze udane uruchomienie **Build Android APK**, pobierz artefakt `sms-samsung-debug-apk`, rozpakuj ZIP i zainstaluj APK. Wersja debugowa wymaga testów i nie służy do dystrybucji produkcyjnej.

## Połączenie z ChatGPT — szkic wiadomości
- W ChatGPT przygotuj tekst i użyj **Udostępnij** → **SMS Samsung** (Android). To najprostsza metoda przekazania treści.
- Alternatywnie aplikacja obsługuje link `smssamsung://compose?text=...` (tekst powinien być zakodowany w URL). Zewnętrzne aplikacje mogą różnie traktować linki niestandardowych schematów.
- Import przekazuje **wyłącznie treść SMS** (do 4000 znaków). Odbiorców wybierasz na telefonie. Żaden SMS nie jest wysyłany automatycznie.
- Wydawanie poleceń w czacie głosowym nie daje aplikacji dostępu do kontaktów i nie uruchamia jej bezpośrednio. Do tego potrzebna byłaby dodatkowa integracja z autoryzowanym backendem i uwierzytelnianiem telefonu.
