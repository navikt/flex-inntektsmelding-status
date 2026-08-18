# flex-inntektsmelding-status


## Inkommenda data
Data input til appen er topicene med tbd.inntektsmeldingstatus, flex.sykepengesoknad og tbd.vedtak.
Sykepengesøknadene brukes til å lage et map mellom orgnummer og orgnavn for å slippe å gjøre eksternt oppslag.

## Cronjobber

### VarselutsendingCronJob

Kjøretid: hverdager 09–15, hvert 15. minutt.

Sender fire typer varsler:

- **Første varsel – mangler inntektsmelding:**
  Sendes når søknaden ble sendt for mer enn 15 dager siden, siste Spleis-status er `VENTER_PÅ_ARBEIDSGIVER`,
  og ingen varsel er sendt tidligere for vedtaksperioden.

- **Andre varsel – mangler inntektsmelding:**
  Sendes når søknaden ble sendt for mer enn 28 dager siden, siste Spleis-status er `VENTER_PÅ_ARBEIDSGIVER`,
  første varsel er sendt, og det har gått minst 10 dager siden forrige varsel.

- **Første varsel – forsinket saksbehandling:**
  Sendes når søknaden ble sendt for mer enn 56 dager siden, siste Spleis-status er `VENTER_PÅ_SAKSBEHANDLER`,
  det har gått minst 12 dager siden forrige varsel, og arbeidsgiver ikke har full refusjon.

- **Revarsel – forsinket saksbehandling:**
  Sendes når Spleis-status er `VENTER_PÅ_SAKSBEHANDLER` og første varsel (eller forrige revarsel) ble sendt
  for mer enn 28 dager siden. Gjentas hver 28. dag så lenge statusen ikke endres.

### SendForelagteOpplysningerCronjob

Kjøretid: hverdager 09–15, hvert 15. minutt.

Sender planlagte forelagte opplysninger fra a-inntekt varsler til brukere med status `NY`.

## Data
Applikasjonen har en database i GCP.

Det slettes ikke fra tabellen med organisasjonsnummer og organisasjonsnavn.

# Komme i gang

Bygges med gradle. Standard spring boot oppsett.

---

# Henvendelser


Spørsmål knyttet til koden eller prosjektet kan stilles til flex@nav.no

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #flex.
