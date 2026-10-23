# Kontekst pracy — SMS Samsung / Doradca.pro

Stan implementacji: 23.10.2026, wersja testowa Android 0.7.0.
Dokument służy do kontynuacji pracy w Codexie lub Android Studio.

## Cel

Lokalny asystent przygotowania pojedynczej wiadomości dla doradcy finansowego:
wybór osoby, jawny cel, treść, podgląd, osobne otwarcie systemowego edytora SMS.
Doradca.pro udostępnia materiały edukacyjne i obsługuje dobrowolne zgłoszenia.
Robocze pozycjonowanie: „Asystent wiadomości Doradca.pro”.
Hasło: „Osobista wiadomość. Twoja decyzja.” Bez deklarowania związku z Samsungiem.

## Repo i stan zmian

- Repo Android: https://github.com/DariuszHalczok/Sms-samsung
- Gałąź: `codex/warm-market-module`; roboczy PR: https://github.com/DariuszHalczok/Sms-samsung/pull/2
- Commit implementacji i dołączonego APK: `833fbb084aad5fa10cf3a5014d8d8f58ae7ed830`.
- Ten dokument dodano później, bez zmian kodu aplikacji.
- Backend referencyjny: `modules/relationship-engine` w prywatnym repo Doradca.pro,
  gałąź `codex/warm-market-api`, stan `001ee2ae9bab3ef4a3cc23fec84c96ed50b79172`.
- PR-y nie są scalone. Brak wdrożenia produkcyjnego. Stary PR Android #1 zastąpił #2.

## Wymagania nadrzędne

Specyfikacja użytkownika Compliance & Security 1.0 z 23.10.2026 zastępuje dawny
projekt „Ciepły Rynek” z przesyłaniem kontaktów i linkami per odbiorca.

- Nigdy nie przesyłać nazw, numerów ani prywatnych treści z telefonu do backendu,
  analityki lub AI. Brak synchronizacji książki, READ_CONTACTS i trwałej bazy klientów.
- Kontakty i szkice tylko w RAM. Można zapisać publiczny UUID profilu doradcy;
  bez sekretów, danych odbiorców i treści.
- Bez tokenów odbiorcy, śledzenia kliknięć przypisanych do numeru i profilowania
  znajomych przed aktywnym zgłoszeniem.
- Wyłącznie pojedynczy `ACTION_SENDTO smsto:` po świadomym działaniu.
  Bez SmsManager, wysyłki w tle, automatycznych kolejek, ponagleń i przechodzenia dalej.
  Otwarcie edytora nie potwierdza wysłania/doręczenia.
- Tryby: prywatny, wcześniej zamówiony materiał, marketing.
  Marketing SMS pozostaje zablokowany do rzeczywistej weryfikacji uprzedniej zgody
  właściwego administratora, celu i kanału. Oświadczenie doradcy jej nie zastępuje.
- Max30 kontaktów to ograniczenie interfejsu, nie podstawa prawna.
- Publiczne linki identyfikują doradcę i materiał, bez PII/sekretów/identyfikatora odbiorcy.
- Anonimowe materiały nie tworzą imiennego leada. Dopiero aktywny formularz tworzy
  zgłoszenie; konsultacja i odrębna zgoda marketingowa to osobne decyzje.
- Wycofanie zgody musi skutecznie blokować marketing.
- Nie wysyłać prawdziwych SMS-ów. Testy nie otwierają edytora SMS.
- Nie deklarować, że ręczna wysyłka zapewnia legalność.

## Zrobione

Po audycie usunięto upload kontaktów, indywidualne linki i dawną kolejkę.
Systemowy picker wskazanego numeru, lokalny CSV bez importu zgód, stan roboczy
poza Bundle/SavedState, FLAG_SECURE, wykluczenie backupu/transferu i wyłączenie
autofill/uczenia klawiatury.

W 0.7: ekran celu/osoby/materiału/treści, usuwanie osoby, potwierdzenia zmian celu
i zastępowania tekstu, zachowanie szkicu przy pierwszym wyborze celu, edytowalne
odpowiedzi na zamówiony materiał, licznik długości/segmentów, obsługa klawiatury
i nieaktualnych odpowiedzi sieciowych. Publiczny profil doradcy jako oddzielne,
walidowane ustawienie; zmiana profilu czyści związany materiał i treść.

Jedyna sieć z aplikacji to GET publicznego katalogu:
`https://doradca.pro/api/relationship/catalog/{public_profile_uuid}`.
Bez odbiorcy, treści, query, tokenu uwierzytelniania, cookies i przekierowań.
Nowy katalog wymaga wdrożenia na backendzie; aplikacja nie tworzy linku zastępczego.

Backend referencyjny ma katalog, anonimowy demonstracyjny kalkulator, dobrowolne
formularze, odrębną zgodę, szyfrowanie, historię/wycofanie i blokady marketingu,
JWT oraz dostęp doradcy do własnego zgłoszenia. Stary POST /api/warm-market/init
zwraca410. Moduł nie jest wdrożony ani połączony z rzeczywistymi kontami CRM.
Przykład kalkulatora nie jest kompletem zatwierdzonych materiałów branżowych.
Sam formularz nie dowodzi posiadania numeru i nie uprawnia do uruchomienia marketingu.

## Wyniki testów i otwarty problem

CI implementacji: https://github.com/DariuszHalczok/Sms-samsung/actions/runs/40024446230

- 24 testy JVM zaliczone; lint0 błędów /11 ostrzeżeń.
- Debug APK, instrumentation APK i niepodpisany release APK zbudowane.
- Kontrola uprawnień obu wariantów i podpis debug APK przeszły.
- Wszystkie17 testów Androida16/API36 zaliczone, zero failures/errors.
- Pięć testów jednostkowych sterowania klawiaturą bez ADB zaliczonych.
- **Całe CI zakończyło się błędem późniejszej hostowej próby śmierci procesu.**
  Skrypt wpisał/odczytał syntetyczny szkic i zamknął proces, ale po ponownym
  uruchomieniu nie znalazł pola: `scripts/android16-smoke.py:113`,
  `AssertionError: Message field not visible`.
  Asercje pustego szkicu i nowego PID nie zostały osiągnięte.
  Nie oznaczać testu jako zaliczonego; nie dowodzi to też odtworzenia treści.
  Sprawdzić fokus/ponowne pokazanie IME/przewijanie po restarcie — przyczyna
  pozostaje niepotwierdzona. Nie usuwać asercji ani FLAG_SECURE.
- Raport Androida: https://github.com/DariuszHalczok/Sms-samsung/actions/runs/40024446230/artifacts/12532222448
- APK: https://github.com/DariuszHalczok/Sms-samsung/actions/runs/40024446230/artifacts/12532501123
- SHA256 APK: `f8b94ba758542d09b9b9f153d82c4a269c0bc581f85e518ac4240eaf6d82e89a`.
- Backend:14 testów/104 asercje, lokalny HTTP smoke i Composer audit zaliczone.
- Brak testu fizycznego Samsunga i wdrożonej integracji produkcyjnej. Nie wysłano SMS.

## Marketing produktu

Folder `marketing/`: lokalna prezentacja HTML, pozycjonowanie, szkice postów,
odpowiedź na zamówione demo, FAQ, scenariusz i14-dniowy pilotaż dla5–10 doradców.
HTML sprawdzony na desktopie i390px, bez zewnętrznych żądań, formularzy i błędów.
W osobnym pakiecie przygotowano jednostronicowy PDF z tej prezentacji.

Materiały są szkicami. Wskaźniki są hipotezami, nie wynikami; brak wymyślonych cen
i opinii. Nie opublikowano strony/reklam, nie uruchomiono kampanii ani wysyłki.
Marketing produktu nie odblokowuje marketingowych SMS w aplikacji.

## Uruchomienie na komputerze

```powershell
git clone --branch codex/warm-market-module https://github.com/DariuszHalczok/Sms-samsung.git
cd Sms-samsung
git status
git rev-parse HEAD
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
py -3 -m unittest discover -s scripts/tests -p "test_*.py"
```

Android Studio, Gradle JDK17, SDK36/Build Tools36, Wrapper8.11.1.
MinSDK26, target35, compile36. Na macOS/Linux: `./gradlew`, `python3`.
APK: `app/build/outputs/apk/debug/app-debug.apk`. Instaluje się na Androidzie8+,
nie na Windows. Podpis debug jest testowy i może kolidować z poprzednią instalacją.
Hostową próbę uruchamiać tylko na dedykowanym emulatorze API36 zgodnie z workflow.
Backend: PHP8.3, Composer, PostgreSQL16; testy tylko na efemerycznej bazie
`relationship_test`, zgodnie z README modułu. Nie kierować ich na produkcyjny CRM.

## Następne kroki

1. Naprawić i ponownie zweryfikować hostowy test po śmierci procesu, bez osłabiania asercji.
2. Odbiór na fizycznym Samsungu, dostępność i stany cyklu życia, dane syntetyczne bez wysyłek.
3. Uzgodnić z operatorem rzeczywisty katalog/profile, adapter CRM/ACL, materiały,
   proces zgód/wycofań/praw osób i bezpieczne wdrożenie.
4. Przejrzeć nazwę, treści i kanał zapisów do pilotażu; bez niezamówionej wysyłki.
5. Zrealizować docs/RELEASE_ACCEPTANCE.md przed produkcją. Nie scalać ani nie wdrażać
   w ramach samego przekazania kontekstu.

## Prompt dla kolejnego Codexa

Kontynuuj SMS Samsung / Doradca.pro Relationship Engine. Przeczytaj ten dokument,
README, docs/COMPLIANCE_AUDIT_2026-10-23.md, docs/RELEASE_0_7.md,
docs/RELEASE_ACCEPTANCE.md i ewentualne AGENTS.md. Sprawdź aktualny HEAD i lokalne
zmiany, zachowaj pracę użytkownika. Gałąź codex/warm-market-module, PR#2.
Najpierw zdiagnozuj hostowy test CI40024446230:17 testów Androida przeszło,
ale skrypt nie znalazł pola po restarcie procesu, linia113. Nie zakładaj odtworzenia
danych ani sukcesu smoke testu. Nie przesyłaj kontaktów, nie dodawaj tokenów
odbiorców/śledzenia, nie odblokowuj marketingu SMS i nie wysyłaj prawdziwych SMS.
Raportuj rzeczywisty stan integracji i testów. Nie scalaj i nie wdrażaj produkcyjnie
bez osobnej instrukcji.
