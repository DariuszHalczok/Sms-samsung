# Pozycjonowanie i rejestr twierdzeń

## Odbiorcy

Główny: doradca finansowy, który po rozmowie chce przygotować jedną wiadomość lub odpowiedzieć na konkretną prośbę o materiał. Drugi: lider zespołu szukający spójnego, zrozumiałego sposobu przygotowywania takich wiadomości. Na etapie pilotażu weryfikujemy obie grupy; nie ogłaszamy popytu jako wyniku badania.

Sytuacja: „Rozmówca poprosił o materiał. Chcę wybrać właściwy link, dopracować krótką odpowiedź i samodzielnie zdecydować o wysłaniu”. Funkcja katalogu zależy od wdrożenia Doradca.pro. Scenariusz demonstracji przed wdrożeniem korzysta z syntetycznych danych testowych i nie sugeruje dostępności produkcyjnej materiałów.

## Propozycja wartości

Hasło: **Osobista wiadomość. Twoja decyzja.**

Opis: „Asystent wiadomości Doradca.pro pomaga przygotować indywidualny szkic i odpowiedź na zamówiony materiał. Wybierz osobę, ustal cel i sprawdź pełną treść przed otwarciem edytora SMS”.

Korzyści do sprawdzenia w pilotażu: mniej ręcznego przepisywania, bardziej czytelna kolejność działań, łatwiejsze dopasowanie odpowiedzi, świadoma kontrola każdego szkicu. Nie przypisujemy im niezmierzonych oszczędności czasu lub wzrostu sprzedaży.

## Co można powiedzieć o 0.7

| Twierdzenie | Potwierdzenie / granica |
|---|---|
| Kontakt wybierasz przez systemowy selektor | `ContactPicker`, manifest bez READ_CONTACTS; fizyczny Samsung wymaga sprawdzenia |
| Kontakty i szkice pozostają w pamięci sesji aplikacji | `DraftSession`, brak SavedState, test procesu i czyszczenia; Android/operator/SMS/klawiatura działają niezależnie |
| Możesz zachować publiczny profil doradcy | `AdvisorProfileStore`: tylko publiczny UUID; bez danych odbiorcy i tokenów |
| Każdy szkic otwierasz osobno | `MainActivity.preview`, ACTION_SENDTO; testy nie uruchamiają edytora ani nie wysyłają SMS |
| Treść i szacowana liczba segmentów są widoczne przed edytorem | `Templates`, `AndroidSmsSizing`; rzeczywisty koszt zależy od operatora |
| Możesz usunąć jedną osobę bez utraty pozostałych | `DraftSession.removeSelected`, testy selekcji i braku automatycznego przejścia |
| Przygotowano odpowiedzi na wcześniejszą prośbę | `RequestedReplies`, dostępne dla wybranego zamówionego materiału; szablon sam nie określa podstawy kontaktu |
| Katalog i formularze Doradca.pro są przygotowywane | Moduł referencyjny istnieje w prywatnym repo, produkcyjne wdrożenie i adapter CRM pozostają otwarte |
| Marketing SMS w aplikacji jest zablokowany | `DraftPolicy`; brak potwierdzonej integracji zgód |

## Twierdzenia wyłączone z komunikacji

„100% zgodności z RODO/PKE”, „ręczny SMS jest zawsze legalny”, „Samsung oficjalnie rekomenduje”, „podwaja sprzedaż”, „gwarantuje doręczenie”, „mierzy odczyt SMS”, „pełna integracja CRM już działa”, „AI analizuje Twoich znajomych”, „nigdzie nie istnieje historia wiadomości”, „30 kontaktów jest bezpieczne prawnie”. Nie ma podstaw do takich deklaracji.

Nie tworzymy pozornie prywatnych pretekstów do niezamówionego marketingu. Przekaz produktu akcentuje rzeczywisty kontekst, wcześniejszą prośbę, indywidualną decyzję oraz jawny stan funkcji.

## Kanały

Najpierw własne materiały publiczne i demonstracja dla osób, które wyraziły zainteresowanie. Własna strona, post autora, prezentacja w uprawnionym kanale zespołu i odpowiedzi na zamówione demo. Udostępnienie szkiców w repo nie jest zgodą na wysyłkę do książki kontaktów ani publikację z kont użytkownika.

Płatne kampanie i cennik dopiero po uzgodnieniu produktu, zakresu pilotażu i kanału obsługi. Nie wdrożono pikseli ani SDK reklamowych. Feedback zbieramy bez danych klientów i zrzutów prywatnych rozmów.
