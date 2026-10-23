# SMS Samsung — AI SMS Assistant

Android 16 / Galaxy S24 Ultra. Quick Start: wybierz numer w kontaktach, edytuj wiadomość i otwórz natywną aplikację SMS z treścią do ręcznego wysłania.

**Nie wysyła automatycznie SMS i nie łączy się jeszcze bezpośrednio z ChatGPT.** Nie wymaga SEND_SMS, odczytu bazy SMS ani kluczy API.

## Instalacja wyłącznie telefonem

1. Otwórz zakładkę **Actions** tego repozytorium.
2. Wybierz **Build Android APK**, **Run workflow**, `main`, **Run workflow** (albo uruchomiony automatycznie build po commicie).
3. Otwórz zakończony sukcesem run i pobierz artefakt `sms-samsung-debug-apk`.
4. Rozpakuj ZIP z artefaktem i otwórz `app-debug.apk` na Samsungu. Zatwierdź instalację z zaufanego źródła, jeśli Android o to poprosi.
5. Otwórz aplikację, wybierz własny numer i przetestuj wiadomość. Wysłanie wymaga kliknięcia w Wiadomościach Google.

Uwaga: APK jest debugowy i nieprzeznaczony do masowej dystrybucji; przed wdrożeniem dla doradców wymagane są testy na urządzeniu, podpisanie wydania i przegląd bezpieczeństwa.
