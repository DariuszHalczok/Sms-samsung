# SMS Samsung — szkice SMS, beta 0.5.0

Android 8+ (min SDK 26), compile SDK 36 / target SDK 35. Docelowy telefon: Samsung Galaxy S24 Ultra / Android 16. **Wersja testowa, integracja backendowa oczekuje na wdrożenie.**

Doradca.pro jest jedynym CRM: odpowiada za klientów, produkty OVB, segmenty, zgody, wykluczenia kontaktu, powtórzenia, rezerwacje i analitykę. Telefon przechowuje tylko roboczą paczkę w pamięci sesji. Nie ma integracji Bitrix.

Nowy etap: [Ciepły Rynek — zakres, pilotaż API i blokery](docs/WARM_MARKET.md). Obejmuje szyfrowaną poczekalnię w nowym module backendu, proponowany kontrakt inicjalizacji oraz kolejkę indywidualnych szkiców. Produkcyjne połączenie i Landing Page pozostają niewdrożone.

## Działanie

- Wybór osób lub grup z telefonu (opcjonalne READ_CONTACTS), import zatwierdzonego CSV UTF-8, maks. 30 unikalnych odbiorców na każdej ścieżce. Numery walidowane przez libphonenumber i normalizowane do E.164 (domyślny kraj PL).
- CSV z przecinkiem/średnikiem, BOM, cytowanymi polami, podwojonymi cudzysłowami i wielowierszowymi dodatkowymi kolumnami. Błędy odrzucają całą paczkę; bez cichego pomijania klientów.
- Edycja/dyktowanie, szablony hipoteczne/polisowe/IKZE, personalizacja `{imie}` / `{imię}`, `{nazwisko}`, `{link}`. Brak wymaganej wartości blokuje podgląd. Szablony startowe nie wymagają linku.
- Podgląd **każdego** odbiorcy, numeru, treści i szacunkowej liczby segmentów GSM-7/Unicode. Koszt zależy od operatora.
- Potwierdzenie sprawdzenia podstaw kontaktu w CRM. To oświadczenie użytkownika, **nie serwerowa weryfikacja zgód**.
- Przycisk otwiera pojedynczy szkic przez `ACTION_SENDTO smsto:`. Wysłanie wymaga decyzji w aplikacji SMS. Status: „szkic” lub „edytor otwarty — wysłanie nieznane”. Brak SEND_SMS, odbioru wiadomości i potwierdzeń doręczenia.
- Obrót ekranu zachowuje stan wyłącznie w ViewModel. Zakończenie Activity lub śmierć procesu kończy sesję; wznowienie kampanii w CRM wymaga osobnego endpointu. Przycisk „Wyczyść sesję” usuwa odbiorców, linki, treść i wyszukiwanie. Historia przechowuje tylko czas i liczbę odbiorców (do 30 wpisów), ma osobne czyszczenie.

## Integracja — jawne ograniczenia

Historyczny GET z dowolnym adresem endpointu i tokenem został usunięty. W 0.5.0 przygotowano oddzielny pilot nowego POST, zgodnego z propozycją modułu Ciepły Rynek. Dostępne materiały nie potwierdzają jego wdrożenia w produkcyjnym Doradca.pro ani OAuth/parowania. Wariant release nie ma INTERNET. W debug 0.5.0 dostępny jest oddzielny pilot POST opisany w WARM_MARKET.md; token doradcy jest używany wyłącznie w RAM na czas żądania. Nie sprawdzono autoryzacji na działającym serwerze. Szczegóły: [audyt i blokery](docs/P0_AUDIT.md).

Stary `smssamsung://compose?text=...` został usunięty z manifestu, aby nie przekazywać treści ani danych klientów w URL. Link z wygasającym opaque kodem kampanii wymaga rzeczywistego backendu i pozostaje wyłączony. Można użyć Android „Udostępnij” → SMS Samsung, sprawdzić tekst i potwierdzić import; odbiorców należy wybrać ponownie. Eksportowany launcher ignoruje obce extras. Nie ma automatycznego połączenia z rozmową ChatGPT.

`{link}` działa tylko z indywidualnym linkiem dostarczonym przez przyszłą autoryzowaną integrację. Obecny CSV przyjmuje imię/nazwę i telefon, bez linków. Walidator przyszłych linków dopuszcza HTTPS na dokładnym hoście doradca.pro, port domyślny/443, bez userinfo, query, fragmentów i kodowania ścieżki. Sam walidator nie potwierdza ważności ani zgody na link. Kliknięcia, boty podglądu, rezerwacje i konwersje musi mierzyć Doradca.pro; zwykły SMS nie pozwala określić odczytu.

## Prywatność

Brak trwałej bazy klientów, zapisów treści/numerów/linków, tokenów, logowania PII i backupu aplikacji. Pola nie zapisują stanu do Bundle; autofill i spersonalizowane uczenie klawiatury są wyłączone. Ekrany i dialogi mają FLAG_SECURE. Systemowa klawiatura, usługa dyktowania, źródłowy CSV i aplikacja SMS mają własne zasady przechowywania danych; aplikacja nie usuwa tych danych. Weryfikacja aktualnych zgód i powtórzeń po stronie CRM pozostaje blokerem produkcyjnym.

## Build i instalacja

JDK 17, Android SDK 36; Gradle Wrapper 8.11.1 z SHA-256 dystrybucji:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

GitHub [Actions](https://github.com/DariuszHalczok/Sms-samsung/actions) uruchamia testy jednostkowe, lint, kompilację i testy instrumentalne na emulatorze API 36. Z udanego uruchomienia pobierz `sms-samsung-debug-apk`, rozpakuj i sprawdź SHA256SUMS. APK jest podpisany kluczem **debug**, którego podpis weryfikuje CI; nie jest to podpis produkcyjny ani stabilny klucz aktualizacji pomiędzy runnerami CI.

```sh
adb install -r app-debug.apk
```

Przy konflikcie podpisów poprzedniego debug APK potrzebna może być deinstalacja starej aplikacji (usuwa jej historię). Ręczna instalacja na Samsungu wymaga zezwolenia na instalację z wybranego źródła. Test urządzeniowy opisano w [planie walidacji](docs/VALIDATION.md). CI nie wysyła SMS-ów i nie uruchamia edytora SMS. Emulator nie zastępuje testu na fizycznym S24 Ultra.
