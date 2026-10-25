# Implementacja: Wyślij zamówiony materiał — audyt żądania klienta (P0)

Stan 2026-10-24: w PR #2 istnieje tryb REQUESTED i ręczny checkbox per odbiorca w `MainActivity.preview`. **Nie istnieje potwierdzony produkcyjny endpoint Doradca.pro do audytowania oświadczeń.** Obecnego checkboxa nie wolno opisywać jako dowodu udzielenia zgody ani wysłania wiadomości.

## Zakres biznesowy
Osoba podczas uprzedniej rozmowy poprosiła o *konkretny* artykuł/kalkulator. Doradca wskazuje materiał i kontakt. Aplikacja przygotowuje neutralną wiadomość ograniczoną do wcześniej zamówionej treści. Na stronie klient może osobno wyrazić zgodę na marketing. Bez prośby klienta tryb REQUESTED nie jest dostępny.

## Docelowy kontrakt Doradca.pro (nie jest obecnie działającym endpointem)
`POST /api/v1/requested-materials/declarations` po HTTPS, OAuth2 PKCE/Bearer, uprawnienie do konkretnego klienta i zespołu. Serwer bierze `advisor_id` z tokena, nie z żądania aplikacji.

Żądanie:
```json
{
  "client_id": "internal-crm-id",
  "material_id": "approved-material-id",
  "request_occurred_at": "2026-10-23T16:00:00+02:00",
  "request_channel": "in_person",
  "circumstances": "Klient poprosił podczas rozmowy o link do kalkulatora.",
  "advisor_attestation": true,
  "idempotency_key": "random-uuid-v4"
}
```
Walidacja: materiał musi być zatwierdzony i niezmienny w ramach deklaracji; deklaracja dotyczy **jednej osoby i jednego materiału**, data nie może pochodzić z przyszłości, wymagane konkretne okoliczności i możliwość wykazania związku doradcy z klientem. Administrator określa politykę retencji i dostępności danych audytu. Rejestrować czas serwerowy, identyfikator doradcy, ID klienta, ID materiału, datę i okoliczności prośby, wersję oświadczenia, wersję szablonu i wynik walidacji. Zmiany przez dopisywanie zdarzeń, nie nadpisywanie.

Odpowiedź po sprawdzeniu autoryzacji:
```json
{
  "declaration_id": "random-opaque-id",
  "status": "recorded_attestation",
  "material_url": "https://doradca.pro/edukacja/...",
  "expires_at": "2026-10-24T16:00:00+02:00"
}
```
Nie zwracać statusu `consent_verified` tylko dlatego, że doradca kliknął potwierdzenie.

## Wymagany przepływ Androida
1. Doradca wybiera tryb REQUESTED, jedną osobę, zatwierdzony materiał, datę i okoliczności wcześniejszej prośby; pola pozostają w RAM.
2. Przed otwarciem edytora aplikacja pokazuje odbiorcę, numer, materiał, treść i potwierdzenie prawdziwości oświadczenia.
3. Android wysyła deklarację do Doradca.pro; jeśli backend jest niedostępny, **nie pokazuje fałszywego statusu zapisano w CRM**. Dla produkcyjnego trybu z obowiązkowym audytem blokuje otwarcie edytora; udokumentowany wyjątek wymaga osobnej decyzji administratora i audytu.
4. Serwer zwraca `declaration_id` i dopiero wtedy użytkownik może ręcznie otworzyć systemowy edytor SMS. Nie używać `SEND_SMS`, nie używać automatycznej kolejki.
5. Stan `editor_opened` oznacza tylko uruchomienie edytora; **nie** dostarczenie, wysłanie ani zgodę marketingową. Wysyłki nie można wiarygodnie poświadczyć bez osobnego, legalnego kanału raportującego.
6. Dla 20–30 osób osobna deklaracja i decyzja per odbiorca. Brak prośby = brak możliwości przygotowania zamówionego materiału w tym trybie.

## Granice compliance
Zgodę marketingową na stronie zbiera odrębnie Doradca.pro (cel, kanał, dobrowolność, brak domyślnego zaznaczenia, odwołanie, log zgody). Link do zamówionego materiału nie może zawierać PII ani tokenów. Link trackingowy nie jest warunkiem uzyskania materiału; statystyki zgodnie z informacją o prywatności. PKE/RODO i teksty SMS wymagają przeglądu prawnego przed produkcją.

## Testy akceptacyjne
- Brak materiału / niepotwierdzona prośba / brak daty / brak okoliczności → brak aktywnego przycisku edytora.
- Prawidłowa deklaracja zapisuje osobny wpis CRM; rozłączenie lub HTTP 401/403/409/5xx nie tworzy statusu sukcesu.
- Przy 30 kontaktach deklaracje nie dziedziczą się pomiędzy osobami.
- Brak numerów i sekretów w logach, URL-ach i preferencjach.
- Testy syntetyczne, nigdy realny SMS.
- Uruchomić unit + Android16 smoke; obecny smoke śmierci procesu na PR #2 ma błąd wymagający naprawy.
