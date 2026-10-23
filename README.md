# SMS Samsung — Relationship Engine 0.7.0

Android 8+ (min26), target35, compile36. Wersja testowa dostosowana do specyfikacji Compliance & Security 1.0 z 23.10.2026. [Audyt przed zmianami](docs/COMPLIANCE_AUDIT_2026-10-23.md) opisuje rozbieżności i zastąpienie projektu poczekalni.

- Nowy ekran kroków: cel, wybrana osoba i treść; sekcja materiałów pojawia się dla wcześniejszej prośby. Można usunąć jedną osobę, a zmiana celu z treścią wymaga potwierdzenia. Odpowiedzi na zamówiony materiał są edytowalne i nadal wymagają potwierdzenia kontekstu przed edytorem.
- Kontakty i prywatne szkice pozostają w RAM telefonu. Brak synchronizacji, trwałej bazy klientów, logowania treści i danych kontaktów, SDK analitycznych oraz integracji AI. Brak READ_CONTACTS: systemowy picker udostępnia jeden wskazany numer. Lokalny CSV pozostaje opcją; import nie przenosi zgód. Max30 to ograniczenie interfejsu.
- Trzy jawne cele: prywatny, wcześniej zamówiony materiał, marketing. **Marketing zablokowany** do czasu rzeczywistej weryfikacji uprzedniej zgody właściwego administratora, celu i kanału SMS. Oświadczenie doradcy ani ręczna wysyłka nie są taką weryfikacją.
- Brak domyślnych pretekstów i szablonów marketingowych. Materiał wybiera się w granicach wcześniejszej prośby. Aplikacja nie klasyfikuje automatycznie prawnego charakteru wpisanego tekstu.
- Użytkownik osobno wybiera odbiorcę, czyta numer i pełną treść, potwierdza kontekst, otwiera `ACTION_SENDTO smsto:`. Brak kolejki, automatycznego przechodzenia, ponagleń, SMS w tle, uprawnień do wysyłania/czytania SMS. Otwarcie edytora nie potwierdza wysłania, doręczenia ani odczytu.
- Jedyna komunikacja sieciowa to GET `https://doradca.pro/api/relationship/catalog/{public_profile_uuid}`. Bez odbiorcy, treści, tokenu doradcy, parametrów query, przekierowań lub cookies zarządzanych przez aplikację. Katalog wymaga wdrożenia nowego modułu na backendzie; niedostępność nie uruchamia zastępczych linków.
- Publiczny link `https://doradca.pro/edukacja/{public_profile_uuid}/{material}` jest wspólny dla odbiorców i identyfikuje tylko doradcę oraz materiał. Dawne `/c/{recipient_token}` i POST kontaktów usunięto we wszystkich wariantach.
- FLAG_SECURE, jawne wykluczenie backupu i transferu urządzenia, stan roboczy poza Bundle/SavedState, brak uczenia klawiatury/autofill. Obrót zachowuje RAM; zamknięcie Activity, śmierć procesu i „Wyczyść sesję” kończą szkice. Jedynym nowym trwałym ustawieniem jest publiczny UUID profilu doradcy (bez tokenów i danych odbiorców). Czyszczenie sesji go zachowuje; usuwa się go osobno w Dodatkowe opcje → Profil doradcy. Zmiana/usunięcie profilu czyści powiązany materiał i treść. Stara lokalna historia agregatów jest usuwana przy uruchomieniu. Android, klawiatura, źródłowy CSV, operator i natywna aplikacja SMS mogą niezależnie przetwarzać dane.

## Integracja i granice gotowości

Nowy moduł Relationship Engine jest w prywatnym repo Doradca.pro. Implementuje publiczny katalog, referencyjny anonimowy kalkulator, aktywne zgłoszenie konsultacji/współpracy/marketingu, odrębny checkbox marketingu, zaszyfrowane dane, historię i wycofanie zgód oraz ACL doradcy. Nie jest wdrożony ani połączony z rzeczywistymi kontami CRM. Kalkulator jest przykładem edukacyjnym; komplet branżowych materiałów wymaga przygotowania i zatwierdzenia. Brak fizycznego testu Samsunga.

Nie wdrażać starych PR/artefaktów 0.4/0.5. Wcześniejsze dokumenty mają oznaczenie historyczne; nowa specyfikacja ma pierwszeństwo. Produkcja wymaga [odbioru i ustalenia ról](docs/RELEASE_ACCEPTANCE.md), rzeczywistej integracji, testu bezpieczeństwa i opinii prawnej.

## Prezentacja i pilotaż

[Pakiet marketingowy](marketing/README.md): lokalna prezentacja HTML, pozycjonowanie, szkice postów i zamówionej prezentacji, scenariusz demo, FAQ oraz 14-dniowy plan pilotażu. Materiały oznaczają stan funkcji i nie deklarują gotowości prawnej, produkcyjnej ani wyników sprzedaży. Nie opublikowano kampanii i nie wysłano wiadomości do innych osób.

## Budowanie i testowanie

JDK17, SDK36, Gradle Wrapper8.11.1 ze sprawdzaną sumą dystrybucji:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
python3 scripts/check-apk-permissions.py --aapt "$ANDROID_HOME/build-tools/36.0.0/aapt" --debug app/build/outputs/apk/debug/app-debug.apk --release app/build/outputs/apk/release/app-release-unsigned.apk
```

[GitHub Actions](https://github.com/DariuszHalczok/Sms-samsung/actions) publikuje `sms-samsung-debug-apk` z `SHA256SUMS` (14dni). Podpis debug jest testowy i może różnić się między runnerami. Rozpakuj, sprawdź `sha256sum -c SHA256SUMS`, zainstaluj `adb install -r app-debug.apk`. Konflikt podpisów może wymagać usunięcia starej wersji. Release APK pozostaje bez podpisu produkcyjnego. CI używa danych syntetycznych; nie wysyła SMS i nie uruchamia edytora SMS.
