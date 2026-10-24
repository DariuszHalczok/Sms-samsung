> Dokument historyczny (0.3–0.5). Wymagania sprzeczne z Compliance & Security 1.0 z 23.10.2026 są wycofane. Aktualny zakres: [audyt](COMPLIANCE_AUDIT_2026-10-23.md) i [README](../README.md). Nie wdrażać poczekalni, linków per odbiorca, synchronizacji kontaktów ani dawnych kolejek.

# Audyt P0 i status integracji — 2026-10-23

Punkt wyjścia Android: `43cc6bcd9c0ea099bdddd95c9bf175118bd5f4de`. Wersja po zmianach: 0.4.0, beta, integration pending. Nie wysyłano SMS-ów.

## Usunięte problemy

| Problem w prototypie | Zmiana |
| --- | --- |
| Rozdzielanie CSV przez split, pojedynczy odczyt InputStream, ciche pomijanie błędów | Parser cytowania RFC4180 z wariantem `;`, ścisły UTF-8, pętla ograniczona do 128 KiB + 1, atomowa paczka |
| Normalizacja usuwała dowolne znaki i dopuszczała błędne numery | libphonenumber, jawny dozwolony alfabet i E.164 |
| Limity tylko w wybranych importerach/podglądzie | Jedna walidacja i limit 30 dla kontaktów, grup, CSV i przyszłych danych API; nadmiar odrzuca całą zmianę |
| Duplikaty mogły nadpisać indywidualny link | Odrzucenie sprzecznych danych po normalizacji numeru |
| Brak `{link}` znikał przed walidacją | Walidacja podczas pojedynczego podstawienia, odrzucenie brakujących/nieznanych pól i przekroczeń długości po personalizacji |
| GET z tokenem: brak permission INTERNET, nieograniczony readBytes, częściowa walidacja adresu, callback po zniszczeniu Activity | Usunięcie niezweryfikowanego przepływu sieciowego i formularza tokenu; brak deklaracji aktywnej integracji |
| API dowolnego click_url, userinfo/port/query i przekierowania | Brak aktywnego klienta; restrykcyjny walidator przyszłych linków z testami. Nie potwierdza on stanu serwera |
| Tekst w deep linku, nieograniczony share, ponowny import przy rotacji | Brak obsługi starego deep linku; osobne wejście plain-text share z limitem, podglądem i potwierdzeniem, przekazanie wyłącznie w pamięci procesu |
| Nietrwały stan przy rotacji, ryzyko zapisu pól UI | ViewModel tylko w pamięci, wyłączony zapis pól, autofill i backup, FLAG_SECURE, pełne czyszczenie sesji, zamykanie dialogów i executor przy onDestroy |
| Podgląd tylko pierwszych czterech wiadomości | Każdy odbiorca ma osobny podgląd przed otwarciem edytora; szacowanie segmentów |
| Brak walidacji CI | Testy jednostkowe, lint i testy instrumentalne API 36, podpis debug i suma APK |

## Przegląd dostępnego materiału Doradca.pro

Autoryzowany odczyt repozytorium właściciela pokazał prototypy UI, dokumentację i fragmenty wdrożeniowe, w tym kontroler Symfony dotyczący dashboardu polis. Nie znaleziono kompletnego uruchamialnego backendu, endpointu kampanii SMS, OAuth PKCE/device authorization ani implementacji jednorazowego kodu kampanii. Nie kopiowano prywatnego kodu ani danych do tego publicznego repozytorium. Brak konfiguracji wdrożenia i konta testowego pozwalających sprawdzić autoryzację na rzeczywistym serwerze. To wynik przeglądu dostępnego materiału, nie twierdzenie o całej infrastrukturze produkcyjnej.

## Blokery produkcyjne (pozostają otwarte)

1. Właściwy backend i faktyczny kontrakt kampanii: wersje/schematy, błędy, linki, czas wygaśnięcia, anulowanie i spójność danych. Nie zgadywać endpointów.
2. OAuth PKCE lub parowanie/device authorization, odwołanie uprawnień, ograniczone krótkie sesje, bezpieczne przechowanie tylko jeśli przyszły przepływ tego wymaga. W tej wersji tokeny nie są przyjmowane.
3. Autoryzacja per doradca/klient/segment, aktualna podstawa kontaktu i cel, sprzeciw/opt-out, ACL, powtórzenia i kolizje doradców. Checkbox lokalny nie zastępuje kontroli serwerowej.
4. Jednorazowy opaque kod kampanii z TTL, powiązaniem z urządzeniem/użytkownikiem, anti-replay i rate limiting. Zweryfikowany HTTPS App Link bez danych klientów/tokenów w URL. Obecnie brak aktywnego deep linku kampanii.
5. Serwerowe generowanie losowych indywidualnych linków, TTL, boty podglądu, informowanie i zgody, rezerwacje/konwersje, audyt, retencja i TLS. Brak open rate dla zwykłego SMS.
6. Test autoryzacji i negatywne testy na serwerze: token cofnięty, cudzy doradca, brak zgody, paczka odwołana, link wygasły, replay, brak sieci. Obecny klient nie udaje ich obsługi.
7. Smoke test fizycznego Galaxy S24 Ultra z Androidem 16 i domyślną aplikacją SMS (bez wysyłki), rzeczywista śmierć procesu/odzyskanie, duże książki kontaktów, odmowa/cofnięcie READ_CONTACTS, usługi dyktowania.
8. Klucz i proces wydania produkcyjnego. CI używa wyłącznie podpisu debug. Brak backendu oznacza, że ten PR nie spełnia pełnych kryteriów produkcyjnych.

Kontakty/grupy są nadal opcjonalnym lokalnym wejściem, a nie drugim CRM. Eksport CSV i dane otwarte w aplikacji SMS podlegają osobnym zasadom retencji. W tej wersji nie ma automatycznej wysyłki, czytania odpowiedzi, dashboardu kliknięć ani synchronizacji z CRM.
