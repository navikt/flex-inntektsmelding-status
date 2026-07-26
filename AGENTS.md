# AGENTS.md - `flex-inntektsmelding-status`
Repoet er en Spring Boot backend som følger status på inntektsmeldinger i Flex.
Den konsumerer Kafka-topics, varsler brukere når inntektsmelding mangler, og
eksponerer et internt API mot `flex-internal-frontend`.

## 1) Kommandoer (må kjøres før commit)

Bruk IntelliJ MCP (`execute_run_configuration`) — se `AGENTS-intellij.md` for detaljer.

| Oppgave       | configurationName                    |
|---------------|--------------------------------------|
| Formater kode | `ktlintFormat`                       |
| Kjør tester   | `flex-inntektsmelding-status [test]` |

## 2) Testing

- Prioriter tester for endret domenelogikk
- Alle tester arver fra `FellesTestOppsett` (Testcontainers med PostgreSQL, Kafka og MockOAuth2Server)
- Bruk `super.slettFraDatabase()` i `@AfterEach` for å rense databasen mellom tester
- Testdata-hjelpere ligger i `src/test/kotlin/no/nav/helse/flex/`

## 3) Prosjektstruktur

```
api/                        REST-kontrollere og DTO-er
brukervarsel/               Utsending av brukervarsel
forelagteopplysningerainntekt/ Forelagte opplysninger fra a-inntekt
inntektsmelding/            Inntektsmelding-domene
kafka/                      Kafka-konfig, consumers og producers
organisasjon/               Orgnummer → orgnavn
sykepengesoknad/            Mottak av sykepengesøknader
varselutsending/            Cronjob for utsending av varsler
vedtaksperiodebehandling/   Vedtaksperiodebehandling
```

**Inngående Kafka:** `tbd.inntektsmeldingstatus`, `flex.sykepengesoknad`, `tbd.vedtak`

**Auth:** Azure AD (issuer `azureator`), inbound fra `flex-internal-frontend`

**Infrastruktur:** Nais/GCP · PostgreSQL med Flyway · Unleash · namespace `flex`

## 4) Kodestil

- All kode, kommentarer og UI-tekst på **norsk bokmål**
- Bruk eksisterende mønstre i koden fremfor nye varianter
- Minimale kommentarer – koden skal være selvforklarende

## 5) Git-workflow

- Egen branch per feature/fix, aldri direkte på `main`
- Hold commit-meldinger korte, beskrivende, én linje, uten punktum
- Ingen conventional commit-prefix (`feat:`/`fix:`) og ingen issue-nummer påkrevd

Standard flyt:

```sh
git checkout -b kort-beskrivende-navn
# formater og test via IntelliJ MCP (se AGENTS-intellij.md)
git commit -m "Kort beskrivelse"
git push origin <branch>
gh pr create --fill
```

## 6) Grenser (aldri gjør dette)

- Aldri lekke eller logge sensitiv informasjon (fnr, tokens, personopplysninger)
- Aldri hardkode hemmeligheter eller credentials
- Aldri commit med rød format/test/build

## 7) Verktøypreferanser

- Foretrekk **IntelliJ MCP** (`com-jetbrains-intellij-*`) for søk, filoperasjoner og kjøring av tester/bygg
- Bruk `search_in_files_by_text`/`search_in_files_by_regex` fremfor grep/ripgrep
- Bruk `find_files_by_glob`/`find_files_by_name_keyword` fremfor shell-basert filsøk
- Se `AGENTS-intellij.md` for hvordan tester og bygg kjøres via IntelliJ MCP

## Når du trenger mer kontekst

- `README.md` - prosjektformål og dataflyt

## Hurtigsjekk før levering

- [ ] Endringen følger eksisterende mønster i berørte filer
- [ ] Tester er oppdatert der domenelogikk er endret
- [ ] format, build og test er grønn
