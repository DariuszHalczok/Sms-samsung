# Doradca.pro → SMS Samsung: import CSV w 0.4.0

Eksportuj zatwierdzonych klientów z Doradca.pro w UTF-8 (opcjonalny BOM), z przecinkiem lub średnikiem. Przykład z fikcyjnym kontaktem testowym, przeznaczony wyłącznie do importu:

```csv
name;phone
Test Example;+12025550101
```

Nagłówek numeru: dokładnie jedna kolumna `phone`, `telefon`, `numer` lub `mobile`. Opcjonalna nazwa: najwyżej jedna z `name`, `imie`, `imię`, `klient`, `nazwisko`. Wielkość liter nagłówków nie ma znaczenia. Pozostałe kolumny są ignorowane. `{nazwisko}` oznacza część nazwy po pierwszej spacji, dlatego eksportuj pełną nazwę, jeśli używasz tego pola.

Cytowanie zgodne z RFC4180: separator i podwojone cudzysłowy wewnątrz pól, zakończenia CRLF/LF. Dodatkowe pola mogą zawierać cytowane nowe linie; nazwa odbiorcy nie może zawierać znaków sterujących. Po zamykającym cudzysłowie wymagany separator, koniec wiersza albo EOF. Brak cichego naprawiania błędnych rekordów.

Limit: 128 KiB, 30 unikalnych numerów, 100 znaków nazwy. Numery PL mogą mieć 9 cyfr; zagraniczne podawaj z `+` lub `00`. libphonenumber sprawdza poprawność planu numeracji, nie właściciela, aktywności numeru ani zgody na SMS.

Identyczne rekordy po normalizacji numeru łączą się; sprzeczne nazwy dla jednego numeru blokują import. Każdy błędny numer, niepoprawny UTF-8, niedomknięty cudzysłów lub różna liczba kolumn odrzuca całą paczkę. Bieżąca paczka zmienia się dopiero po udanym imporcie i potwierdzeniu. CSV nie importuje linków ani potwierdzeń zgód.

Na telefonie wybierz „Import CSV z Doradca.pro”, potwierdź zastąpienie listy, edytuj treść i przejrzyj każdego odbiorcę. Przed przeglądem potwierdź weryfikację uprawnień, celu, podstawy kontaktu, sprzeciwów i powtórzeń w CRM. Każda wiadomość wymaga osobnego otwarcia edytora SMS i ręcznej decyzji w nim.

Plik CSV pozostaje w wybranym źródle; aplikacja go nie kopiuje i nie usuwa. Nie przechowuje klientów po zakończeniu sesji. Doradca.pro pozostaje jedynym CRM. Import lokalny nie weryfikuje serwerowo zgód i nie jest pełną integracją produkcyjną.
