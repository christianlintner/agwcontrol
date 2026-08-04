# Issue #21 — API-Vergleichs-Report über alle Umgebungen

## Übersicht

Ziel ist ein schlanker CSV-Report (`report_api_comparison_<ts>.csv`), der **alle Umgebungen nebeneinander vergleicht**. Pro eindeutigem `api_name + api_version` gibt es eine Zeile. Pro Umgebung erscheint genau **eine Bool-Spalte** (`<ENV>_present`), die anzeigt ob die API dort vorhanden ist.

**Abgrenzung zu Issue #12:** Issue #12 liefert den vollständigen Cross-Env-Report mit 10 Spalten pro Umgebung. Dieses Issue ist bewusst schlanker: 4 feste API-Spalten + 1 Bool-Spalte pro Umgebung.

**Betroffene Dateien:**
- `app/src/main/java/com/agwcontrol/DbReportService.java`
- `app/src/test/java/com/agwcontrol/DbReportServiceTest.java`

---

## Sub-Task 1 — Konstante `API_COMPARISON_HEADER_PREFIX` hinzufügen

**Status:** `[x] done`

**Intent:** Eine eigene Konstante für den Header-Prefix des neuen Reports einführen, analog zu `CROSS_ENV_HEADER_PREFIX`. Sie dient als Ankerpunkt für Tests und macht den Code selbstdokumentierend.

**Expected Outcomes:**
- `DbReportService` hat eine neue `static final String API_COMPARISON_HEADER_PREFIX` mit dem Wert `"api_name;api_version;api_type;api_active"`.

**Todo List:**
- [x] In `DbReportService.java` direkt unterhalb von `CROSS_ENV_HEADER_PREFIX` die neue Konstante einfügen.

**Relevant Context:**
- `DbReportService.java` Zeile 25 — bestehende `CROSS_ENV_HEADER_PREFIX`-Konstante als Vorlage.

---

## Sub-Task 2 — Methode `buildApiComparisonCsv()` implementieren

**Status:** `[x] done`

**Intent:** Die eigentliche Pivot-Logik für den Vergleichs-Report implementieren. Pro Env wird nur geprüft, ob eine API dort existiert (Boolean), nicht welche Endpoints oder Check-Ergebnisse vorhanden sind.

**Expected Outcomes:**
- Neue `public`-Methode `buildApiComparisonCsv()` in `DbReportService`.
- Header: `API_COMPARISON_HEADER_PREFIX` + `;<ENV>_present` je Env (Envs **alphabetisch sortiert**).
- Datenzeilen: eine pro eindeutigem `api_name|api_version`, alphabetisch sortiert.
- Env-Spalte: `true` wenn API in dieser Env vorhanden, sonst leer (`""`).
- Leerfall (keine Daten): Header-Zeile + `(keine Daten vorhanden)` — analog zu `buildCrossEnvCsv()`.

**Todo List:**
- [x] `db.loadEnvironments()` aufrufen, Ergebnis alphabetisch sortieren.
- [x] Header-String aus `API_COMPARISON_HEADER_PREFIX` + `;<ENV>_present` je Env aufbauen.
- [x] Pivot-Map befüllen: `rowKey = api_name|api_version` → `Set<String>` der Envs, in denen die API vorkommt. Pro Env `db.loadApis(env)` aufrufen; `apiByKey`-Map für die 4 festen Felder pflegen.
- [x] Leerfall prüfen und ggf. Hinweiszeile ausgeben.
- [x] Sortierte Zeilen ausgeben: 4 feste Felder (`api_name`, `api_version`, `api_type`, `api_active`) + `true`/`""` je Env.

**Relevant Context:**
- `DbReportService.java` — `buildCrossEnvCsv()` als strukturelles Vorbild (Pivot-Muster mit `apiByKey` + `LinkedHashMap`).
- `db.loadApis(env)` liefert `List<ApiInfo>` — keine Endpoints oder Checks nötig.
- `csvField()` für String-Felder verwenden.

---

## Sub-Task 3 — `writeReports()` um den neuen Report erweitern

**Status:** `[x] done`

**Intent:** Den neuen Comparison-Report in den bestehenden Report-Schreibvorgang integrieren, sodass er bei jedem `writeReports()`-Aufruf automatisch erzeugt wird.

**Expected Outcomes:**
- `writeReports()` erzeugt zusätzlich die Datei `report_api_comparison_<ts>.csv`.
- Rückgabewert `count` erhöht sich um 1 (war: n Envs + 1; neu: n Envs + 2).
- Konsolenausgabe analog zu den bestehenden Dateien.

**Todo List:**
- [x] Nach dem Block für `crossEnvCsv` in `writeReports()` den neuen Block einfügen: `buildApiComparisonCsv()` aufrufen, Datei schreiben, `count++`.
- [x] Bestehende Tests, die `assertEquals(3, created)` prüfen, auf `assertEquals(4, created)` anpassen (betrifft `writeReportsCreatesFilePerEnvironment` und `writeReportsCrossEnvFile` — beide in `DbReportServiceTest.java`).

**Relevant Context:**
- `DbReportService.java` Zeilen 56–63 — Cross-Env-Block als direktes Vorbild.
- `DbReportServiceTest.java` Zeilen 188 und 312 — die beiden `count`-Assertions, die angepasst werden müssen.

---

## Sub-Task 4 — Tests ergänzen

**Status:** `[x] done`

**Intent:** Die neue Methode `buildApiComparisonCsv()` und die Datei-Erstellung vollständig durch Unit-Tests absichern.

**Expected Outcomes:**
- 4 neue Tests in einer neuen Sektion `// --- API-Comparison-Report ---` in `DbReportServiceTest.java`.
- Alle bestehenden Tests bleiben grün.

**Todo List:**
- [x] `apiComparisonHeaderContainsAllEnvironments()` — Header beginnt mit `API_COMPARISON_HEADER_PREFIX`, enthält `DN2020-DEV_present` und `DN2020-PreProd_present`.
- [x] `apiComparisonMarksPresenceCorrectly()` — API in DEV + PreProd → beide Spalten `true`.
- [x] `apiComparisonEmptyForMissingEnvironment()` — API nur in DEV → PreProd-Spalte ist leer; Anzahl der Felder stimmt (4 + Anzahl Envs).
- [x] `writeReportsCreatesApiComparisonFile()` — Datei `report_api_comparison_*.csv` existiert nach `writeReports()`; Rückgabewert korrekt.

**Relevant Context:**
- `DbReportServiceTest.java` — bestehende Cross-Env-Tests (`crossEnvHeaderContainsAllEnvironments`, `crossEnvOneRowPerApi`, `crossEnvEmptyColumnsForMissingEnvironment`) als Strukturvorlage.
- Für den Presence-Test reicht `db.saveApis()` — kein `saveEndpoints()` oder `saveCheckResult()` nötig, da `buildApiComparisonCsv()` nur `loadApis()` verwendet.
