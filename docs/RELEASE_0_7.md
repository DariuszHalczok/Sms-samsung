# Wydanie testowe 0.7 — interfejs i marketing produktu

Rozwinięcie 0.6 po audycie zgodności. Nie zmienia modelu przesyłania danych ani blokady marketingu SMS. Nowy backend nie jest wdrażany w ramach tej zmiany.

## Zmiany

- Ekran XML z sekcjami celu, wybranej osoby, zamówionego materiału i treści. Zasoby tekstowe, motyw, ikona i wielkości w dp/sp zamiast surowych pikseli.
- Wybór aktywnej osoby i usunięcie jednego kontaktu; bez kolejki i automatycznego wybierania kolejnego. Ponowny wybór tego samego numeru aktualizuje tylko jego nazwę.
- Pierwszy jawny wybór celu zachowuje zaimportowany szkic. Potwierdzenie przed wyczyszczeniem treści przy późniejszej zmianie celu; anulowanie zachowuje szkic. Zastąpienie treści gotową odpowiedzią także wymaga decyzji.
- Publiczny profil doradcy zapisany w oddzielnym ustawieniu. Walidacja UUID, usuwanie nieznanych pól, brak tokenów, danych kontaktów i treści. „Wyczyść szkic i kontakty” go zachowuje; opcje profilu umożliwiają usunięcie. Zmiana profilu unieważnia powiązany materiał/treść i wynik żądania w toku.
- Przygotowane odpowiedzi wyłącznie dla wybranego zamówionego materiału; wybór szablonu nie ustala podstawy prawnej. Każdy podgląd wymaga nowego oświadczenia o kontekście.
- Bieżące informacje o długości szkicu przed personalizacją, pełny przeliczony podgląd przed edytorem. Stan wczytywania i komunikaty o wyniku pominiętym po zmianie szkicu.
- Obsługa insetów klawiatury/systemu i adjustResize: pole treści pozostaje dostępne podczas wpisywania. Próba procesu wykrywa klawiaturę, zamyka ją przed inspekcją i przewija wyłącznie formularz; przy błędzie zapisuje strukturę widoków bez tekstów.
- Activity Result API dla pickera i pliku, bez trwałego grantu URI. Wykluczenia backupu i transferu urządzenia, FLAG_SECURE bez zmian.
- Pakiet marketingowy opisuje produkt i proponowany pilotaż. Nie uruchomiono reklam, nie opublikowano strony i nie wysłano wiadomości do odbiorców. Nie włączono marketingu SMS w aplikacji.

## Walidacja

Testy JVM sprawdzają limit, deduplikację, wybór i usuwanie bez automatycznego przejścia, zachowanie stanu po błędzie, zmianę celu i ograniczenia odpowiedzi. Instrumentacja sprawdza osobne ustawienie profilu, brak danych kontaktów w preferencjach, usuwanie uszkodzonych danych, anulowanie zmiany celu i świeże potwierdzenie szkicu. Pozostają testy kontaktów, żądania HTTPS bez danych odbiorcy, RAM/rotacji, obcych extras, marketingu oraz hostowa próba śmierci procesu na emulatorze API36.

Prezentację sprawdzono w Chromium: desktop i telefon 390px, działanie kotwicy CTA, brak poziomego przewijania, zewnętrznych żądań, błędów JS i formularzy. Wygenerowano jednostronicowy PDF A4 do przeglądu. To schemat procesu, nie zrzut ekranu aplikacji.

Dokładne wyniki CI, commit i artefakty są w opisie PR. Fizyczny Samsung, rzeczywisty katalog/CRM, materiały branżowe, obsługa praw osób i formalne warunki produkcji pozostają zgodne z [dokumentem odbioru](RELEASE_ACCEPTANCE.md).
