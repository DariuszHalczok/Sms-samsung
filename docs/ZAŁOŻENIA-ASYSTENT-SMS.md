# Założenia produktu — SMS Samsung / Asystent AI
Data: 2026-10-23

## Uzgodniona potrzeba
- Dariusz chce wysyłać **partie po 20–30 indywidualnych SMS-ów** do dotychczasowych klientów, nie kampanie liczone w setkach lub tysiącach.
- Klienci **znają jego numer telefonu**; SMS ma wyjść **z tego samego numeru**, a odpowiedź ma przyjść do standardowej aplikacji SMS i prowadzić do normalnej rozmowy z klientem.
- Główny cel: obsługa i kontakt z klientami mającymi zawarte umowy, zachęcenie ich do odpowiedzi lub oddzwonienia.
- Baza: lokalne kontakty Android / synchronizowane Google Contacts, wybieranie osób i grup.
- Polecenie może być dyktowane w ChatGPT, docelowo także wydawane na komputerze. Integracja ChatGPT ↔ telefon wymaga osobnego mostu; NIE jest zaimplementowana.
- AI ma pomóc sformułować lub personalizować treść i zinterpretować wskazaną grupę.
- Przed wysyłką ma być widoczna lista odbiorców i treść oraz wyraźne potwierdzenie użytkownika; unikanie duplikatów, błędnych numerów i pomyłek.
- Na początek indywidualne SMS-y (nie wspólny grupowy wątek MMS), kontrola wysyłki i historii po stronie telefonu.
- Potrzebny prosty przepływ obsługi na **komputerze**, gdzie telefon pozostaje urządzeniem nadającym z własnej SIM. Alternatywa do przeanalizowania: Google Messages for web; nie zakładać, że pozwala na zautomatyzowaną integrację AI.

## Prywatność i zgody
- Użytkownik deklaruje zgodę/podstawę przetwarzania danych osobowych klientów w związku z zawartymi umowami. To NIE jest równoznaczne z uprzednią zgodą na marketing SMS.
- Rozdzielić informacje dotyczące realizacji/obsługi umowy i marketing bezpośredni; kwalifikować cel przed wysyłką oraz weryfikować wymagane zgody i sprzeciwy.
- Minimalizować zakres danych przekazywanych do AI i serwerów. Książka kontaktów powinna pozostawać na urządzeniu, chyba że istnieje wyraźna potrzeba i autoryzacja.
- Limity, logi potwierdzeń, tryb testowy, ostrzeżenia przed masową wysyłką; sprawdzać limity operatora i Androida.

## Aktualny stan repo
- Wersja beta 0.2: wyszukiwanie kontaktów, wybór wielu osób, grup dostępnych systemowo, dyktowanie treści na urządzeniu, uruchamianie edytora SMS dla każdego odbiorcy.
- NIE ma jeszcze: integracji głosowego ChatGPT z aplikacją; obsługi z komputera; automatycznego wysyłania paczki; kontroli rzeczywistego statusu dostarczenia; testu end-to-end na telefonie.
- Nie należy nazywać tych funkcji gotowymi przed testami urządzeniowymi.

## Proponowane następne etapy
1. Zweryfikować build i działanie wersji beta 0.2 na S24 Ultra; sprawdzić etykiety/grupy Google.
2. Dopracować selekcję odbiorców, deduplikację numerów, potwierdzenia, podgląd 20–30 wiadomości, tryb testowy na własny numer.
3. Zaprojektować parowanie urządzenia oraz bezpieczny interfejs z komputera/ChatGPT; uwierzytelnienie, krótkotrwałe polecenia i akceptacja na telefonie.
4. Po sprawdzeniu ograniczeń systemu/operatora i zasad komunikacji ocenić indywidualną wysyłkę z SIM versus otwieranie edytora wiadomości.
