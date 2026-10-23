# Doradca.pro → SMS Samsung: szybki import CSV

## Etap 1: działa lokalnie, bez API i bez dodatkowej bazy klientów
Po filtrowaniu klientów w Doradca.pro wyeksportuj CSV w UTF-8:
```csv
name;phone
Jan Kowalski;+48500111222
Anna Nowak;+48500333444
```
Obsługiwane nagłówki numeru: `phone`, `telefon`, `numer`, `mobile`.
Obsługiwane nagłówki nazwy: `name`, `imie`, `imię`, `klient`, `nazwisko`.
Separator: przecinek lub średnik; bez pól zawierających separator w cudzysłowach.
Maksymalnie 30 unikalnych odbiorców i 128 KB na plik. Polski numer zapisuj najlepiej jako `+48XXXXXXXXX`.

## Telefon
SMS Samsung → Importuj listę klientów z Doradca.pro (CSV) → wybierz plik → potwierdź → wpisz SMS → sprawdź → otwórz wiadomość w systemowej aplikacji SMS.
Nadal każdą wiadomość zatwierdzasz osobno. APK nie potwierdza doręczenia ani kliknięcia linku.

## Etap 2: docelowa integracja Doradca.pro
Doradca.pro zarządza filtrami produktów, uprawnieniami, zgodami, listami kampanii, indywidualnymi linkami i analityką kliknięć. Telefon nie powinien przechowywać trwałej kopii CRM.
Rekomendacja: jednorazowy krótko żyjący kod kampanii + uwierzytelniony HTTPS backend (OAuth/parowanie telefonu), pobranie listy przez aplikację po zatwierdzeniu użytkownika. Nie umieszczać numerów telefonów, tokenów sesji ani nazwisk w publicznych deep-linkach.
Zwykły SMS nie umożliwia wiarygodnego wykrycia odczytania. Kliknięcia unikalnych linków można mierzyć na serwerze Doradca.pro z odpowiednimi informacjami dla odbiorców.
Nie implementować komunikacji backendowej bez uzgodnienia rzeczywistych endpointów i mechanizmu autoryzacji Doradca.pro.
