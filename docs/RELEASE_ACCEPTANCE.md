# Odbiór i granice wdrożenia

Dokument roboczy, bez deklaracji opinii prawnej lub podpisanego odbioru. Role administratora/współadministratora/procesora wymagają formalnego ustalenia.

| Zakres | Wymagany właściciel ustalenia/realizacji |
|---|---|
| Poprawność kodu, aktualizacje, dokumentacja, usuwanie podatności | Dostawca aplikacji w zakresie umowy |
| Cele, podstawa kontaktu, treść i zakres marketingu | Organizacja/doradca zgodnie z rzeczywistą rolą prawną |
| Administratorzy, zgody/notice, retencja, prawa osób | Ustalone podmioty z obsługą prawną; operator realizuje uzgodnione procesy |
| Hosting, szyfrowanie/klucze, TLS, ACL, kopie, logi, incydenty, formularze/antybot | Operator Doradca.pro i dostawcy według umów |
| Bezpieczeństwo wydania, klucz podpisu, test urządzenia i formalny odbiór | Właściciel wydania z dostawcą i operatorem |

Przed produkcją: zakres umowy i wymagania bezpieczeństwa; zatwierdzona dokumentacja; macierz obowiązków; regulamin; informacje o danych dla każdego procesu; umowy przetwarzania według ról; procedura nielegalnego użycia; aktualizacji i incydentów; protokół testów i odbioru. Nie stosować fikcyjnego wyłączenia całej odpowiedzialności programisty.

Opinia PKE/RODO, analiza ryzyka (DPIA jeśli wymagana), zatwierdzone cele/treści/version zgód i privacy notice są otwarte. Zmiany wymagań bezpieczeństwa wymagają odrębnej decyzji projektowej, oceny ryzyka i zatwierdzenia prawnego.

## Testy odbiorowe

Automatyczne: walidacja/CSV/30/deduplikacja, nieznany cel i marketing blokowane; publiczne URL bez odbiorcy, transport przyjmuje tylko publiczny profil; manifest obu APK bez kontaktów/SMS/audio; jawne potwierdzenie jednej wiadomości; pamięć po obrocie i czyszczeniu, ignorowanie extras; kontrakty katalogu; emulator API36 i śmierć procesu.

Backend: GET katalogu/materiału bez tworzenia osób; stary POST410 bez zapisu; świadomy formularz z celem; konsultacja bez marketingu; dowód zgody i historia wycofania; wycofanie blokuje inne zgłoszenia dla tego samego numeru/celu/administratora; CSRF, Origin, honeypot, expiry, replay, rate; szyfrowanie; JWT/odwołanie; dostęp tylko właściciela; retencja.

Ręcznie przed podpisaniem odbioru: fizyczny Samsung S24 Ultra Android16, picker pojedynczego kontaktu i brak szerokiego dostępu (różnice dostawców); anulowanie, brak aplikacji SMS, obrót/zabicie procesu; przechwycenie ruchu na danych syntetycznych i kontrola logów produkcyjnego proxy; komplet materiałów i formularzy; wycofanie/odmowa/anonimowy kalkulator; test rzeczywistych ról i wyłączonego konta; test praw osób i kopii; test bezpieczeństwa/nadużyć i audyt zależności. Bez wysyłania rzeczywistych SMS podczas testów Codexa.

Incydent: ograniczyć ujawniony przepływ i odwołać sesje/klucze według zakresu, zabezpieczyć dowody bez kopiowania PII do publicznych zgłoszeń, powiadomić ustalonego operatora/administratora; właściwy podmiot ocenia obowiązki notyfikacji. Konkretne kontakty, SLA i kanał zgłoszenia nadużyć muszą wejść do umowy i konfiguracji wydania. Nie są obecnie zatwierdzone.

## Priorytety

P0: usunięcie synchronizacji i śledzenia, najmniejsze uprawnienia, blokada marketingu i testy. P1: materiały zamówione, katalog i zgłoszenia po aktywnym formularzu, Consent Engine. P2: dopiero zatwierdzone agregaty anonimowe i rozwój treści. AI, profile znajomych, sekwencyjne follow-upy i indywidualne śledzenie przed zgłoszeniem nie należą do tego wydania.
