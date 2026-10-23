# Walidacja 0.4.0

## Automatyczna

- JVM: normalizacja PL/zagraniczna, błędne numery/rozszerzenia, atomowa zmiana paczki 1/20/30/31, duplikaty i konflikt linków, pola szablonów, brak linku, limity po podstawieniu, polityka URL, segmenty GSM-7/Unicode i emoji.
- CSV: BOM/UTF-8, aliasy, CRLF/LF, cytowane separatory/podwojone cudzysłowy/nowe linie w dodatkowej kolumnie, częściowe odczyty strumienia, nieprawidłowe UTF-8, rozmiar, błędne cudzysłowy/kolumny/numer, duplikaty, 1/20/30/31.
- Android API 36 emulator: rotacja z zachowaniem sesji w pamięci, czyszczenie, nowa Activity po zakończeniu nie ma szkicu, ignorowanie nieufnych extras/starego deep linku, wymagane potwierdzenie weryfikacji zgód, intencja jednego edytora i odporność historii na uszkodzony JSON. Test intencji **nie uruchamia** aplikacji SMS.
- Osobny smoke ADB na emulatorze API 36: wpisanie syntetycznego szkicu, przeniesienie aplikacji w tło, `am kill` bez usuwania zadania, uruchomienie nowego procesu i sprawdzenie, że szkic nie wraca. Wyłącznie metadane pass/fail, bez uruchamiania edytora SMS.
- Lint i kompilacja APK z manifestem bez SEND_SMS/INTERNET. CI weryfikuje podpis debug przez apksigner i publikuje SHA256SUMS.

Wynik kompilacji, wynik emulatora i test fizycznego telefonu to odrębne dowody. Rzeczywiste wyniki są powiązane z uruchomieniem Actions i opisem PR. Nie traktować powyższej listy jako deklaracji zaliczonych testów przed zakończeniem CI.

## Smoke fizycznego S24 Ultra / Android 16 — wymagany przed produkcją

Używać wyłącznie danych testowych. **Nie wysyłać wiadomości.**

1. Zainstalować podpisany testowy APK, sprawdzić wersję 0.4.0, układ przy krawędziach ekranu, dużej czcionce i rotacji.
2. Odmówić READ_CONTACTS, ponowić wybór, przyznać dostęp, później go cofnąć. Oczekiwane: brak awarii/wycieku, czytelny komunikat.
3. Zaimportować 1, 20 i 30 rekordów. 31, błędny numer, zepsuty CSV i >128 KiB mają odrzucić całą zmianę. Sprawdzić duplikaty w różnych formatach i łączny limit przy dodawaniu grup/kontaktów.
4. Edytować szablon z `{link}` bez linku, nieznanym polem, długim podstawieniem i polskimi znakami. Sprawdzić blokowanie i liczbę segmentów. Każdego odbiorcę/treść przejrzeć osobno.
5. Potwierdzenie sprawdzenia podstaw kontaktu musi być wymagane. Nie używać klientów bez podstawy kontaktu; serwerowa kontrola braku zgody pozostaje niezaimplementowana.
6. Otworzyć jeden szkic w domyślnej aplikacji SMS, sprawdzić pojedynczy numer i treść, wrócić **bez wysyłania**. Status nie może potwierdzać wysłania/dostarczenia.
7. Obrócić ekran: szkic i odbiorcy zachowani. „Wyczyść sesję”: znika treść, numery, nazwy, linki i wyszukiwanie. Zakończyć aplikację i wykonać force-stop/ponowne otwarcie: dane klienta nie wracają. Sprawdzić, że rotacja/ubita Activity podczas importu nie otwiera starego dialogu ani nie podmienia nowej paczki.
8. Udostępnić plain text: wymaga podglądu i potwierdzenia, czyści wcześniejszych odbiorców. Nadmiarowy tekst i stare deep linki nie są importowane.
9. Tryb offline nie może wyświetlać pozornego sukcesu API. Formularz tokenu i pobieranie kampanii pozostają niedostępne. Testy odwołanego tokenu i wygasłego kodu wymagają backendu testowego.
10. Historia zawiera tylko czas/liczbę szkiców; wyczyszczenie historii ją usuwa. Sprawdzić dyktowanie, brak usługi i przekroczenie 4000 znaków. Nie dodawać logów z danymi testowymi, treścią czy tokenami.
