# Audyt kontaktów Google i synchronizacja z Doradca.pro — zadanie dla Codex / Claude Code
Data: 2026-10-23
Status: SPECYFIKACJA; brak importu i brak zmian danych produkcyjnych.

## Cel
Zbudować bezpieczny, powtarzalny proces porównania książek kontaktów z dwóch kont Google:
- halczokdariusz@gmail.com
- dariusz.halczok.ovb@gmail.com
oraz danych klientów w Doradca.pro. Następnie udostępnić uporządkowane kontakty w aplikacji Android (obecnie SMS Samsung, docelowo moduł Doradca.pro).
**Nie pobieraj danych z treści maili jako zamiennika książki adresowej.**

## Aktualne ograniczenie
Integracja Google Contacts dostępna w ChatGPT pozwala szukać konkretnych osób, lecz nie udostępnia pełnego listowania/eksportu wszystkich kontaktów ani wyboru obu kont. Pełny audyt wymaga autoryzowanego eksportu Google Contacts (CSV/vCard) z OBU kont lub implementacji OAuth Google People API z odpowiednimi uprawnieniami read-only. Nie twierdzić, że analiza została wykonana, zanim dane zostaną odczytane.

## Etap 1 — wyłącznie odczyt
1. Uzyskaj osobne, jawne autoryzacje do obu książek Google; preferuj contacts.readonly. Obsłuż paginację People API, limity, metadane źródła, eTag; ewentualnie wczytaj dwa pliki CSV z eksportu.
2. Pobierz ze zgodnego z uprawnieniami API Doradca.pro tylko minimalne pola: internal_customer_id, imię/nazwisko, telefony, maile, owner/advisor, status relacji, zgody marketingowe, sprzeciwy, daty aktualizacji. Najpierw potwierdź schemat i role użytkowników; nie domyślaj się istnienia endpointów.
3. Trzymaj dane kontaktów i pliki eksportowe w prywatnym szyfrowanym obszarze, nigdy w publicznym GitHub, logach CI, promptach AI ani commitach.
4. Normalizuj telefony wg E.164 (np. biblioteka libphonenumber, region PL z wyjątkami dla zagranicy), porządkuj Unicode/diakrytyki i maile. Nie zmieniaj oryginalnych wartości.
5. Stwórz raport read-only: liczba rekordów wg źródła, unikalne osoby i numery, rekordy bez telefonu, telefony niepoprawne, dublety wewnątrz i między kontami, konflikty nazw/telefonów, kontakty niepowiązane z CRM, klienci CRM bez kontaktu mobilnego.
6. Dopasowanie: exact ID (tylko gdy naprawdę wspólne), następnie zgodny telefon E.164, email, fuzzy name wyłącznie jako kandydat do ręcznego zatwierdzenia. Uwzględnij współdzielone numery (rodzina/firma) i kontakt posiadający wiele numerów.
7. Wygeneruj tabelę propozycji: source_ids, crm_customer_id, match_basis, confidence, proposed_action, original_values, proposed_values, conflicts, review_status. Nie wykonuj automatycznego scalania na podstawie samego imienia.

## Etap 2 — zatwierdzenie i kontrolowana synchronizacja
- Domyślnie dry-run, backup każdej książki, podgląd diffu, selektywna akceptacja użytkownika.
- Żadnego usuwania, nadpisywania czy przenoszenia danych bez odrębnej zgody.
- Operacje idempotentne z audit logiem oraz opcją rollback.
- Model źródła prawdy: CRM przechowuje status klienta i jego zgody, książki telefoniczne przechowują kontakty prywatne; nie nadpisuj danych osobistych automatycznie danymi CRM.
- Relacje klient/doradca: kontrola uprawnień, nie ujawniać całej struktury/portfela doradcom.

## Etap 3 — moduł Android / Doradca.pro
- Nazwa produktu powinna być niezależna od Samsung; wspieraj urządzenia Android zgodne z minSdk i rzeczywistymi testami.
- Wybór odbiorców z książki urządzenia lub autoryzowanego API Doradca.pro; filtrowanie wg doradcy i uprawnień; podgląd listy i treści.
- Generowanie szkiców SMS (bez przesyłania niepotrzebnych danych osobowych do modelu); personalizacja za zgodą użytkownika.
- Odseparuj wiadomości obsługowe od marketingowych; RODO i PKE: podstawa przetwarzania danych nie zastępuje wymaganej zgody marketingowej, obsługa sprzeciwów/wycofań.
- Batching 20–30 INDYWIDUALNYCH wiadomości z zatwierdzeniem; żadnego niekontrolowanego automatycznego wysyłania.
- SMS z własnej SIM: najpierw potwierdź ograniczenia Android/Google Play/operatora; beta otwiera natywny edytor, nie potwierdza dostarczenia.
- Synchronizacja zdarzeń do CRM tylko po potwierdzonym statusie; rozróżniaj draft, opened_editor, user_reported_sent, platform_confirmed_sent, delivered; nie udawaj statusów.

## Testy akceptacyjne
- Brak modyfikacji podczas dry-run.
- Polski numer zapisany jako 9 cyfr, +48 i 0048 jest jednym numerem; zagranicznych numerów nie dopasowuj na siłę.
- Dwóch klientów ze wspólnym numerem nie scala się automatycznie.
- Cofnięcie importu jest możliwe na danych testowych.
- Brak zgody marketingowej skutkuje blokadą marketingowych SMS-ów.
- Porównanie 2 kont + CRM z raportem konfliktów i wyraźnym źródłem każdej danej.
- Działanie aplikacji na różnych producentach telefonów, nie tylko Galaxy S24 Ultra.

## Kolejność wdrażania
1. Dokończ, przetestuj i podpisz instalator Android oraz testy na 2–3 markach.
2. Przeprowadź eksport/przegląd obu źródeł Google wyłącznie do odczytu.
3. Potwierdź schemat i uprawnienia Doradca.pro; wykonaj raport rozbieżności.
4. Zatwierdź reguły scalania i dopiero potem uruchom kontrolowaną synchronizację.
5. Dodaj bezpieczną integrację CRM → Android i osobny etap AI.
