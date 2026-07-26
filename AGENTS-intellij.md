# IntelliJ MCP-instruksjoner

Bruk alltid IntelliJ MCP-verktøy (`com-jetbrains-intellij-*`) fremfor bash/grep/glob der det finnes ekvivalent funksjonalitet.

Dette er et Gradle-basert Spring Boot-prosjekt (Kotlin). Bygg og tester kjøres via IntelliJ MCP.

## Kjøre tester og bygg

Bruk `execute_run_configuration` med `configurationName` lik en eksisterende Gradle-konfigurasjon:

| Oppgave               | configurationName                    |
|-----------------------|--------------------------------------|
| Formater kode         | `ktlintFormat`                       |
| Alle tester           | `flex-inntektsmelding-status [test]` |
| Starte applikasjonen  | `Application`                        |

Eksempel:
```
execute_run_configuration(
  configurationName: "flex-inntektsmelding-status [test]",
  projectPath: "/Users/.../flex-inntektsmelding-status",
  waitForExit: true,
  timeout: 1200000
)
```

For rask kompilering og feilsjekk uten å kjøre tester, bruk `build_project` (evt. `get_file_problems` på en enkelt fil).

Testene bruker Testcontainers (PostgreSQL + Kafka), så Docker må kjøre og første kjøring kan ta noen minutter.

### Kjøre én enkelt testklasse eller testmetode

1. `get_run_configurations` med `filePath` satt til testfilen for å finne run points (linjenumre for klasse og hver `@Test`)
2. `execute_run_configuration` med samme `filePath` og `line` fra ønsket run point, `waitForExit: true` og `timeout: 1200000`

Eksempel – kjør én testmetode:
```
execute_run_configuration(
  filePath: "src/test/kotlin/no/nav/helse/flex/FlexInternalFrontendApiAuthTest.kt",
  line: 62,
  projectPath: "/Users/.../flex-inntektsmelding-status",
  waitForExit: true,
  timeout: 1200000
)
```

## Opprette ny run-konfigurasjon

Run-konfigurasjoner er lagret i `.idea/workspace.xml`. IntelliJ har ingen MCP-verktøy for å opprette konfigurasjoner — de må legges til manuelt i XML-filen.

### Steg

1. Åpne `.idea/workspace.xml`
2. Finn en eksisterende `<configuration ... type="GradleRunConfiguration" ...>`-blokk
3. Legg til en ny blokk med samme mønster rett etter:

```xml
<configuration name="KONFIGNAVN" type="GradleRunConfiguration" factoryName="Gradle" temporary="true">
  <ExternalSystemSettings>
    <option name="executionName" />
    <option name="externalProjectPath" value="$PROJECT_DIR$" />
    <option name="externalSystemIdString" value="GRADLE" />
    <option name="scriptParameters" />
    <option name="taskNames">
      <list>
        <option value="GRADLE_TASK" />
      </list>
    </option>
    <option name="vmOptions" />
  </ExternalSystemSettings>
  <method v="2" />
</configuration>
```

4. Erstatt `KONFIGNAVN` med et beskrivende navn og `GRADLE_TASK` med Gradle-tasken (f.eks. `test`, `build` eller `ktlintFormat`)
5. Bruk `execute_run_configuration` — IntelliJ plukker opp endringen uten omstart

> Konfigurasjoner i `workspace.xml` er lokale og skal ikke committes. `.idea/` er i `.gitignore`.

## Søk og navigasjon

- `search_in_files_by_text` / `search_in_files_by_regex` for tekstsøk i koden
- `search_symbol` for å finne klasser, funksjoner og andre symboler
- `find_files_by_glob` / `find_files_by_name_keyword` for filsøk
- `read_file` for å lese filer
