# Co brakuje do połączenia SMS Samsung z Doradca.pro

Stan opisu: 24.10.2026. Podstawa: kod Androida 0.7.0 (commit `833fbb0`),
późniejsze dokumenty i skrypt testowy z `1466d16`, moduł referencyjny
`modules/relationship-engine` (`001ee2a`) oraz dokument odbioru.
W tym przeglądzie nie sprawdzano produkcyjnego API ani nie wysyłano formularzy/SMS.

## Zakres pierwszego działającego połączenia

Doradca ustawia swój publiczny profil, aplikacja pobiera katalog, a następnie
pozwala przygotować odpowiedź z materiałem, o który odbiorca wcześniej poprosił.
Odbiorca otwiera materiał anonimowo i może sam zgłosić prośbę o konsultację.
Dopiero jego aktywny formularz może utworzyć zgłoszenie po stronie Doradca.pro.

Telefon nie przesyła kontaktów ani treści SMS. Nie tworzymy tokenów konkretnych
odbiorców, historii wysyłek na serwerze ani powiązania kliknięcia z numerem.
Marketing SMS w aplikacji pozostaje zablokowany.

## Co jest już przygotowane

| Element | Obecny stan |
| --- | --- |
| Android: publiczny profil | Ręczne wpisanie i zapis publicznego UUID v4; bez hasła/tokenu |
| Android: pobranie katalogu | GET przez HTTPS, walidacja JSON i stałych linków materiałów |
| Android: wiadomość | Kontakt i szkic lokalnie w RAM, podgląd i pojedynczy ACTION_SENDTO |
| Backend: katalog i strony | Implementacja referencyjna, bez potwierdzonego wdrożenia na doradca.pro |
| Backend: formularze | Dobrowolne zgłoszenia konsultacji/współpracy/marketingu, oddzielna zgoda |
| Backend: ochrona danych | Szyfrowanie, zdarzenia/wycofanie zgody, blokady marketingu i referencyjny JWT/ACL |
| Rzeczywiste konta i CRM | Brak gotowego adaptera działającego systemu Doradca.pro |

## Co musi dostarczyć operator Doradca.pro

| Brak / zadanie | Rezultat potrzebny do odbioru |
| --- | --- |
| Wdrożenie routingu i modułu | Osiągalny katalog oraz strony materiałów pod uzgodnionymi adresami HTTPS |
| Powiązanie publicznego profilu z kontem | Stały publiczny UUID v4 doradcy, przypisany do jego rzeczywistego konta; obsługa wyłączonego konta |
| Materiały | Zatwierdzone treści i kalkulatory dla rzeczywiście udostępnionych kategorii |
| Formularze i CRM | Świadome zgłoszenie trafia do właściwego doradcy z właściwym celem, bez automatycznego tworzenia osoby przy samym wejściu |
| Konta, uprawnienia i odwołania | Rzeczywisty adapter tożsamości i ACL; inny doradca nie widzi zgłoszenia, wyłączone konto traci dostęp |
| Konfiguracja i utrzymanie | Baza/migracje, klucze, retencja/purge, backup, logi bez PII, TLS, limity formularzy i procedury incydentów |
| Zgody i prawa osób | Ustalone role administratorów, zatwierdzone cele/treści/wersje, wycofanie, eksport/usunięcie i retencja zgodnie z podstawami |

Konfiguracja referencyjnego backendu zawiera mapę publiczny profil → konto
(`RELATIONSHIP_PROFILES_JSON`) oraz politykę administratora/celu/zgody/retencji
(`RELATIONSHIP_POLICY_JSON`). To nie jest gotowy panel administracyjny CRM.
Klucze szyfrowania i dane uwierzytelniające pozostają na serwerze; nie trafiają
do APK, publicznych linków ani dokumentów w repozytorium.

Obecny demonstracyjny kalkulator regularnego oszczędzania jest wspólnym przykładem.
Nie stanowi kompletu kalkulatorów hipoteki, refinansowania, ochrony rodziny,
emerytury, IKZE i firmy. Katalog powinien udostępniać tylko przygotowane materiały.

## Dokładny kontrakt katalogu oczekiwany przez APK 0.7.0

```http
GET https://doradca.pro/api/relationship/catalog/{public_profile_uuid}
Accept: application/json
```

Profil jest publicznym identyfikatorem doradcy: UUID v4 zapisany małymi literami.
Przykład poniżej używa wyłącznie syntetycznego UUID, nie istniejącego konta:

```json
{
  "public_profile": "00000000-0000-4000-8000-000000000001",
  "materials": [
    {
      "id": "hipoteka",
      "url": "https://doradca.pro/edukacja/00000000-0000-4000-8000-000000000001/hipoteka"
    }
  ]
}
```

Wymagania zgodności z obecnym klientem:

- HTTP 200, Content-Type `application/json` (możliwy charset), poprawny UTF-8,
  odpowiedź do 16 KiB; timeout połączenia i odczytu po 10 sekund.
- Dokładnie pola główne `public_profile`, `materials`; w pozycji dokładnie `id`, `url`.
  Dodanie innych pól wymaga uzgodnienia zmiany klienta.
- Zwrócony profil musi odpowiadać żądanemu. Od 1 do 6 unikalnych pozycji.
  Identyfikatory: `hipoteka`, `refinansowanie`, `ochrona-rodziny`, `emerytura`, `ikze`, `firma`.
- Link musi dokładnie odpowiadać
  `https://doradca.pro/edukacja/{public_profile_uuid}/{id}`.
- Bez przekierowania do www/logowania, parametrów query, odbiorcy, treści SMS
  lub tokenu sesji. Transport aplikacji nie dołącza uwierzytelnienia ani cookies.
- Nie zwracać HTML logowania lub danych CRM pod adresem katalogu. Niedostępność
  nie może uruchamiać uploadu kontaktów ani generowania linku zastępczego.

Pobranie katalogu jest publiczne. Pierwsze połączenie nie wymaga logowania
w Androidzie ani wydania aplikacji tajnego klucza API. Udostępnienie profilu doradcy
w jego panelu CRM i przepisanie UUID do aplikacji wystarcza do tego etapu.

## Gdzie potrzebna jest pełna integracja CRM

Po aktywnym formularzu trzeba zapisać właściwy cel zgłoszenia i powiązać je
z doradcą oraz ustalonym administratorem. Prośba o konsultację nie oznacza zgody
na przyszły marketing. Odmowa marketingu nie może blokować konsultacji i materiałów.

Referencyjny `GET /api/relationship/submissions/{id}` ma weryfikację JWT i dostęp
właściciela; wymaga podłączenia do prawdziwych kont, kluczy, odwołań i reguł CRM.
Nie jest gotowym logowaniem mobilnym ani systemem parowania telefonu. Aktualna
aplikacja Android nie wywołuje tego endpointu i nie pokazuje zgłoszeń z CRM.
Taki ekran i uwierzytelnianie to osobny zakres implementacji.

Consent Engine ma referencyjne sprawdzanie aktualnej zgody i wycofań. Nie ma
bezpiecznej integracji Androida z tą funkcją. W przyszłości dotyczyłaby tylko osób,
które same się zgłosiły, przez identyfikator rekordu i właściwe uprawnienia.
Nie dodawać wyszukiwania zgód przez przesyłanie numerów z książki telefonicznej.
Samo uruchomienie katalogu lub formularza nie odblokowuje trybu marketingowego.

### Propozycja audytu zamówionego materiału nie jest wdrożonym API

W [REQUESTED_MATERIAL_AUDIT_P0.md](REQUESTED_MATERIAL_AUDIT_P0.md) zaproponowano
`POST /api/v1/requested-materials/declarations`, OAuth2 PKCE i zapis oświadczenia
doradcy dla konkretnego `client_id`. Nie ma potwierdzonego endpointu produkcyjnego
ani implementacji tego wywołania w APK. Nie jest to warunek techniczny publicznego
katalogu opisanego powyżej.

Ta propozycja wymaga przeglądu względem nadrzędnej specyfikacji Compliance & Security.
Nie wolno tworzyć `client_id` przez upload/dopasowywanie książki telefonicznej,
rejestrować na serwerze historii odbiorców z telefonu przed ich zgłoszeniem ani
przesyłać opisu prywatnej rozmowy pod pozorem audytu. Pseudonimowy identyfikator osoby
także nie usuwa tego ograniczenia. Bezpieczny, oddzielny projekt może dotyczyć
wyłącznie już zgłoszonych osób w CRM, przy ustalonych celach, podstawie, minimalizacji
i uprawnieniach. Samo oświadczenie doradcy nie jest zweryfikowaną zgodą marketingową.
Nie wdrażać nowego śledzenia lub rejestru na podstawie samej propozycji dokumentu.

## Informacje do uzyskania od operatora

1. Osoba odpowiedzialna za backend/wdrożenie i potwierdzenie aktualnej architektury CRM.
2. Potwierdzenie obsługi powyższego kontraktu albo lista konkretnych różnic do uzgodnienia.
3. Publiczny UUID testowego doradcy i lista rzeczywiście gotowych materiałów.
4. Miejsce widoczności dobrowolnych zgłoszeń w CRM i reguły dostępu do nich.
5. Uzgodniony proces tożsamości, blokowania kont, zgód/wycofań i praw osób.
6. Środowisko odbioru i właściciel logów/proxy, aby sprawdzić przepływ danych.

Nie potrzebujemy hasła doradcy ani sekretu produkcyjnego w czacie. Obecne APK
ma stały host `https://doradca.pro`; dowolnego adresu API nie wpisuje się w UI.
Dla osobnego hosta staging trzeba uzgodnić i przygotować osobny wariant testowy
z zatwierdzonym hostem. Alternatywą wymagającą ustalenia z operatorem jest wydzielony
profil/testowe zasoby na obecnym hoście. Nie wyłączać TLS ani walidacji URL.

## Próba odbiorowa — bez wysyłania SMS

1. Wpisać rzeczywisty publiczny UUID testowego doradcy w ustawieniach aplikacji.
2. Pobrać katalog i porównać odpowiedź z kontraktem; sprawdzić błąd sieci,
   nieznany profil, wyłączone konto i nieprawidłową odpowiedź.
3. Na danych syntetycznych sprawdzić, że ruch zawiera wyłącznie publiczny profil,
   bez kontaktów, treści, Authorization i identyfikatora odbiorcy.
4. Otworzyć materiał bez formularza i potwierdzić, że nie powstaje imienny lead.
5. W środowisku odbioru złożyć syntetyczne zgłoszenie konsultacji bez marketingu;
   potwierdzić poprawnego doradcę/cel oraz brak automatycznej zgody marketingowej.
6. Sprawdzić historię i wycofanie zgody, skuteczną blokadę przyszłego marketingu
   oraz dostęp tylko właściwych ról. Nie wysyłać wiadomości w celu sprawdzania blokady.
7. Sprawdzić logi, szyfrowanie, usuwanie danych testowych i uzgodnioną retencję.

Najkrótszy zakres pierwszego odbioru: działający katalog + prawdziwy publiczny
profil + przynajmniej jeden zatwierdzony materiał. Pełny przepływ zgłoszenia
wymaga dodatkowo formularza i adaptera CRM.

Przed wydaniem aplikacji pozostaje też znany błąd hostowego testu po restarcie
procesu oraz odbiór na fizycznym Samsungu. Nie są one dowodem niedziałania katalogu,
ale wymagają zamknięcia przed formalnym odbiorem. Problem pobierania APK jest
odrębnym zadaniem dystrybucji. Szczegóły gotowości: [warunki odbioru](RELEASE_ACCEPTANCE.md)
i [kontekst pracy](KONTEKST_PRACY_SMS_SAMSUNG.md).

Ostatnie sprawdzone CI skryptu: [40093409789](https://github.com/DariuszHalczok/Sms-samsung/actions/runs/40093409789),
commit `1466d16`; zadanie APK przeszło, zadanie Androida zakończyło się błędem.
Dokumentacja nie zmienia wyniku testów ani nie oznacza akceptacji wydania.

## Tekst do przekazania zespołowi Doradca.pro

> Aplikacja SMS Samsung 0.7.0 ma klienta publicznego katalogu materiałów, bez
> przesyłania kontaktów. Prosimy o potwierdzenie wdrożenia
> GET https://doradca.pro/api/relationship/catalog/{public_profile_uuid},
> przydzielenie publicznego UUID testowego doradcy i udostępnienie co najmniej
> jednego zatwierdzonego materiału pod /edukacja/{profile}/{id}.
> Prosimy też o wskazanie, jak aktywny formularz utworzy zgłoszenie z właściwym
> celem u właściwego doradcy oraz jak będą obsłużone ACL, odwołania kont,
> wycofanie zgody i prawa osób. Moduł referencyjny wymaga adaptera działającego CRM
> i konfiguracji utrzymania. Pierwszy odbiór wykonamy na danych syntetycznych,
> bez wysyłania SMS. Marketing SMS pozostaje zablokowany.
