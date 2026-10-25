# Propozycja pilotażu — 14 dni

Plan do zatwierdzenia, bez rozpoczętej rekrutacji i bez zebranych wyników. Grupa proponowana: 5–10 dobrowolnie zgłoszonych doradców oraz 1–2 liderów. Najpierw test użyteczności na danych syntetycznych. Komunikacja z rzeczywistymi osobami wymaga odrębnego zatwierdzenia zakresu, prawnych podstaw i formalnego odbioru.

## Harmonogram

| Termin | Działanie | Rezultat do zebrania |
|---|---|---|
| Dni 1–2 | Potwierdzenie opiekuna, zakresu, zgłoszeń uczestników i urządzeń; wydanie APK z checksum | Lista uczestników obsługiwana właściwym kanałem organizatora, bez książek klientów |
| Dni 3–5 | Zadania indywidualne na syntetycznych kontaktach; obserwacja jasności interfejsu | Zagregowany czas wykonania i miejsca niezrozumienia |
| Dni 6–9 | Poprawki problemów i powtórny test najtrudniejszych kroków | Lista błędów technicznych bez treści/telefonów klientów |
| Dni 10–12 | Test procesu materiału, jeśli wdrożony i zatwierdzony; w innym razie jawna demonstracja kontraktu | Oddzielne oznaczenie rzeczywistego backendu i fixture |
| Dni 13–14 | Podsumowanie i decyzja właściciela: kolejny etap lub poprawki | Protokół wniosków, brak automatycznej publikacji |

## Zadania testowe

1. Wybrać prywatny cel i jedną syntetyczną osobę, przygotować własny tekst, sprawdzić podgląd; zatrzymać się przed edytorem SMS.
2. Dodać drugą osobę, usunąć ją, sprawdzić zachowanie pierwszej i treści; potwierdzić brak automatycznego przejścia.
3. Zmienić cel, anulować ostrzeżenie i zachować treść, następnie świadomie zatwierdzić zmianę.
4. Zapisać testowy publiczny profil doradcy, wyczyścić szkic i potwierdzić, że profil jest osobnym ustawieniem; usunąć profil.
5. Sprawdzić obrót, większą czcionkę, brak internetu, brak selektora lub aplikacji SMS, zabicie procesu. Nie wysyłać SMS w testach Codexa.
6. Sprawdzić niedostępny katalog i zablokowany marketing; komunikaty powinny jasno mówić, co można zrobić dalej.

## Metryki — cele do weryfikacji

| Miara | Proponowane kryterium oceny |
|---|---|
| Samodzielne ukończenie scenariusza prywatnego do podglądu | Co najmniej 80% uczestników; mała próba, bez uogólnienia na rynek |
| Zrozumienie różnicy „edytor otwarty” / „wysłano” | Każdy uczestnik potrafi ją wyjaśnić po zadaniu |
| Zrozumienie blokady marketingu i stanu katalogu | Każdy uczestnik rozpoznaje oba ograniczenia |
| Czas przygotowania syntetycznego szkicu | Mediana i rozrzut, porównanie do procesu uczestnika bez obietnicy poprawy |
| Utrata treści po anulowaniu zmiany celu | 0 przypadków |
| Nieoczekiwane automatyczne przejście lub wysłanie | 0 przypadków; każde zgłoszenie zatrzymuje pilotaż |

Brak analityki odbiorców i pikseli. Uczestnik może dobrowolnie przekazać czas wykonania i opinię o interfejsie. Nie zbieramy kontaktów, treści prywatnych SMS, zrzutów rozmów, tokenów ani identyfikatorów potencjalnych klientów. Dokumentacja techniczna w repo opisuje osobno ewentualne dane dobrowolnych zgłoszeń.

## Warunki dalszego etapu

Uzgodniony właściciel produktu i obsługa zgłoszeń; faktyczne wdrożenie właściwego backendu; opinia PKE/RODO i ustalone role; zaakceptowane treści/zgody; test bezpieczeństwa i fizycznego Samsunga; podpisany protokół odbioru. Wymagania: [odbiór wydania](../docs/RELEASE_ACCEPTANCE.md). Przygotowany marketing nie zastępuje tych kroków.
