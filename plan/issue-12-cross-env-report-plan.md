# Plan: Cross-Environment Report (Issue #12)

## Übersicht

Beim Aufruf von `report` soll zusätzlich zu den bestehenden pro-Umgebung-CSV-Dateien eine weitere Datei erzeugt werden, die **alle Umgebungen in einer einzigen Tabelle** zusammenfasst. Pro API gibt es eine Zeile; pro Umgebung einen Spaltenblock mit 9 Werten.

**Branch:** `feature/issue-12-cross-env-report`

**Betroffene Dateien:**
- [`DbReportService.java`](app/src/main/java/com/agwcontrol/DbReportService.java)
- [`DbReportServiceTest.java`](app/src/test/java/com/agwcontrol/DbReportServiceTest.java)

---

## Pivot-Schlüssel (Zeilenidentifikation)

Der Schlüssel für eine Zeile im Cross-Env-Report ist:

```
api_name | api_version
```

> **Begründung:** Eine API ist über alle Umgebungen hinweg durch `api_name` + `api_version` eindeutig identifizierbar. Endpoint-URLs und Alias-Namen sind umgebungsspezifisch und werden pro Umgebung in den jeweiligen Spaltenblock übernommen. Hat eine API in einer Umgebung mehrere Endpoints, wird nur der **erste** Endpoint verwendet.

---

## Spaltenstruktur

```
api_name;api_version;api_type;api_active
  ;<DN2020-DEV>_alias_name;<DN2020-DEV>_endpoint_url;<DN2020-DEV>_ping_ok;<DN2020-DEV>_ping_ms;<DN2020-DEV>_tcp_ok;<DN2020-DEV>_tcp_ms;<DN2020-DEV>_http_status;<DN2020-DEV>_reachable;<DN2020-DEV>_error_msg;<DN2020-DEV>_checked_at
  ;<DN2020-PreProd>_alias_name;<DN2020-PreProd>_endpoint_url;...
```

> Die festen API-Spalten sind **4** (`api_name`, `api_version`, `api_type`, `api_active`). Pro Umgebung gibt es **10 Spalten** (`alias_name` + `endpoint_url` + 8 Check-Felder).

---

## Beispiele

### Beispiel 1 — Normaler Fall: gleiche API, verschiedene URLs je Umgebung

**Daten in der DB:**
- DN2020-DEV: `Payments API v1`, Alias `gw1`, URL `https://dev-gw1/pay`, HTTP 200, reachable: true
- DN2020-PreProd: `Payments API v1`, Alias `gw1`, URL `https://preprod-gw1/pay`, HTTP 200, reachable: true

**Erwartete CSV-Ausgabe (gekürzt):**

```
api_name;api_version;api_type;api_active;DN2020-DEV_alias_name;DN2020-DEV_endpoint_url;DN2020-DEV_reachable;...;DN2020-PreProd_alias_name;DN2020-PreProd_endpoint_url;DN2020-PreProd_reachable;...
Payments API;v1;REST;true;gw1;https://dev-gw1/pay;true;...;gw1;https://preprod-gw1/pay;true;...
```

→ **Eine Zeile** für `Payments API v1`, mit env-spezifischen Alias-Namen und URLs.

---

### Beispiel 2 — Fehlende Daten für eine Umgebung

**Daten in der DB:**
- DN2020-DEV: `Payments API v1`, Alias `gw1`, URL `https://dev-gw1/pay`, HTTP 200, reachable: true
- DN2020-PreProd: *(kein Eintrag für diese API)*

**Erwartete CSV-Ausgabe (gekürzt):**

```
api_name;api_version;api_type;api_active;DN2020-DEV_alias_name;DN2020-DEV_endpoint_url;DN2020-DEV_reachable;...;DN2020-PreProd_alias_name;DN2020-PreProd_endpoint_url;...
Payments API;v1;REST;true;gw1;https://dev-gw1/pay;true;...;;;;;;;;;;
```

→ **Eine Zeile**, die 10 leere Felder für DN2020-PreProd enthält.

---

### Beispiel 3 — Keine Umgebungen / keine Daten

**Daten in der DB:** leer

**Erwartete CSV-Ausgabe:**

```
api_name;api_version;api_type;api_active
(keine Daten vorhanden)
```

→ Header-Zeile (nur feste Spalten, da keine Umgebungen bekannt) + eine Hinweiszeile `(keine Daten vorhanden)`.

> Hinweis: `writeReports()` schreibt diese Datei trotzdem und gibt sie in der Konsolenausgabe aus.

---

### Beispiel 4 — Gleiche API in beiden Umgebungen, mehrere Endpoints in DEV

**Daten in der DB:**
- DN2020-DEV: `Payments API v1` mit Endpoints `gw1 → https://dev-gw1/pay` und `gw2 → https://dev-gw2/pay`
- DN2020-PreProd: `Payments API v1` mit Endpoint `gw1 → https://preprod-gw1/pay`

**Erwartete CSV-Ausgabe:**

```
api_name;api_version;api_type;api_active;DN2020-DEV_alias_name;DN2020-DEV_endpoint_url;...;DN2020-PreProd_alias_name;DN2020-PreProd_endpoint_url;...
Payments API;v1;REST;true;gw1;https://dev-gw1/pay;...;gw1;https://preprod-gw1/pay;...
```

→ **Eine Zeile** — in DN2020-DEV wird nur der erste Endpoint (`gw1`) verwendet. `gw2` wird ignoriert.

---

## Sub-Tasks

### Sub-Task 1 — Konstante `CROSS_ENV_HEADER_PREFIX` hinzufügen

**Status:** `[x] done`

**Intent:**  
Die festen vier API-Spalten des Cross-Env-Headers als benannte Konstante bereitstellen, analog zur bestehenden `HEADER`-Konstante. Diese Konstante wird in Tests direkt referenziert und verhindert Magic Strings.

**Expected Outcomes:**  
- `DbReportService` enthält `static final String CROSS_ENV_HEADER_PREFIX = "api_name;api_version;api_type;api_active"`.
- Bestehende Tests bleiben grün.

**Todo List:**
1. In [`DbReportService.java`](app/src/main/java/com/agwcontrol/DbReportService.java) direkt nach der `HEADER`-Konstante die neue Konstante `CROSS_ENV_HEADER_PREFIX` einfügen.

**Relevant Context:**
- Bestehende Konstante: [`DbReportService.HEADER`](app/src/main/java/com/agwcontrol/DbReportService.java:24)

---

### Sub-Task 2 — Methode `buildCrossEnvCsv()` implementieren

**Status:** `[x] done`

**Intent:**  
Die eigentliche Pivot-Logik: alle Umgebungen laden, für jede Umgebung den ersten Endpoint + Check-Daten laden, nach `api_name|api_version` gruppieren und eine CSV mit einer Zeile pro API und einem 10-Spalten-Block pro Umgebung erzeugen.

**Expected Outcomes:**  
- `buildCrossEnvCsv()` gibt einen String zurück, der mit einer dynamisch generierten Header-Zeile beginnt.
- Pro Umgebung enthält der Header die Spalten: `<ENV>_alias_name`, `<ENV>_endpoint_url`, `<ENV>_ping_ok`, `<ENV>_ping_ms`, `<ENV>_tcp_ok`, `<ENV>_tcp_ms`, `<ENV>_http_status`, `<ENV>_reachable`, `<ENV>_error_msg`, `<ENV>_checked_at`.
- Eine Zeile pro eindeutigem `api_name|api_version`-Schlüssel.
- Pro Umgebung wird nur der **erste** Endpoint (erster Treffer aus `loadEndpoints()`) herangezogen.
- `alias_name` und `endpoint_url` werden pro Umgebung aus dem Endpoint-Eintrag befüllt; Check-Daten aus dem passenden `CheckRow` (Abgleich über `resolved_url`).
- Fehlende Daten für eine Umgebung (API nicht vorhanden oder kein Endpoint) → 10 leere Felder.
- Sind gar keine Daten vorhanden → Header + Zeile `(keine Daten vorhanden)`.
- Zeilen sortiert nach `api_name + api_version`.
- Umgebungen alphabetisch sortiert (kommt von `db.loadEnvironments()`).
- Bestehende `CheckRow`-Klasse und `loadCheckRows()`-Methode werden wiederverwendet.

**Todo List:**
1. Neue public-Methode `public String buildCrossEnvCsv() throws SQLException` in [`DbReportService.java`](app/src/main/java/com/agwcontrol/DbReportService.java) hinzufügen.
2. `db.loadEnvironments()` aufrufen und Header dynamisch aufbauen: `CROSS_ENV_HEADER_PREFIX` + pro Env `;<ENV>_alias_name;<ENV>_endpoint_url;<ENV>_ping_ok;<ENV>_ping_ms;<ENV>_tcp_ok;<ENV>_tcp_ms;<ENV>_http_status;<ENV>_reachable;<ENV>_error_msg;<ENV>_checked_at`.
3. Für jede Umgebung alle APIs laden. Pro API den ersten Endpoint aus `db.loadEndpoints()` holen sowie passende Check-Rows aus `loadCheckRows()`. Pivot-Map aufbauen: `LinkedHashMap<String rowKey, Map<String env, PivotEntry>>` — Schlüssel = `api_name|api_version`, `PivotEntry` enthält `ApiInfo`, `RoutingEndpoint` (erster) und `CheckRow` (erste passend zur URL; null wenn kein Check vorhanden).
4. Zeilen nach `api_name + api_version` sortiert ausgeben. Pro Zeile: 4 API-Felder + pro Umgebung 10 Felder (leer falls kein Eintrag in der Map).
5. Falls die Pivot-Map leer ist: Hinweiszeile `(keine Daten vorhanden)` ausgeben.
6. Bestehende Hilfsmethode `csvField()` und `CheckRow` wiederverwenden.

**Relevant Context:**
- [`DbReportService.loadCheckRows()`](app/src/main/java/com/agwcontrol/DbReportService.java:128) — liefert `List<CheckRow>` für Env + apiId; enthält `resolvedUrl` und `aliasName`.
- [`ApiDatabase.loadEndpoints()`](app/src/main/java/com/agwcontrol/ApiDatabase.java:180) — liefert `List<RoutingEndpoint>`; erster Eintrag wird verwendet.
- [`ApiDatabase.loadEnvironments()`](app/src/main/java/com/agwcontrol/ApiDatabase.java:213) — liefert alphabetisch sortierte Env-Liste.
- `CheckRow.resolvedUrl` wird verwendet um den passenden Check-Eintrag zum ersten Endpoint zu finden.

---

### Sub-Task 3 — `writeReports()` um Cross-Env-Datei erweitern

**Status:** `[x] done`

**Intent:**  
Nach dem Schreiben der pro-Umgebung-Dateien zusätzlich `buildCrossEnvCsv()` aufrufen und das Ergebnis als `report_all_environments_<ts>.csv` in dasselbe Verzeichnis schreiben. Die Datei wird **immer** geschrieben — auch wenn keine Daten vorhanden sind (dann mit Hinweiszeile). Rückgabewert (`count`) wird um 1 erhöht.

**Expected Outcomes:**  
- `writeReports()` schreibt neben den pro-Env-Dateien immer auch eine `report_all_environments_<ts>.csv`.
- Rückgabewert = Anzahl Env-Dateien + 1.
- Konsolenausgabe analog zu den bestehenden Env-Dateien (z. B. `Erstellt: report_all_environments_<ts>.csv`).

**Todo List:**
1. Nach der `for`-Schleife in [`writeReports()`](app/src/main/java/com/agwcontrol/DbReportService.java:43) `buildCrossEnvCsv()` aufrufen.
2. Datei `report_all_environments_<ts>.csv` schreiben und `count++` ausführen.
3. Konsolenausgabe hinzufügen.

**Relevant Context:**
- [`DbReportService.writeReports()`](app/src/main/java/com/agwcontrol/DbReportService.java:43) — Zeitstempel `ts` ist bereits vorhanden und kann direkt genutzt werden.

---

### Sub-Task 4 — Tests in `DbReportServiceTest` ergänzen

**Status:** `[x] done`

**Intent:**  
Die vier geforderten Tests implementieren, um das korrekte Verhalten von `buildCrossEnvCsv()` und `writeReports()` automatisiert zu verifizieren.

**Expected Outcomes:**  
Alle vier neuen Tests sind grün:
- `crossEnvHeaderContainsAllEnvironments()` — Header enthält `DN2020-DEV_alias_name`, `DN2020-DEV_endpoint_url`, `DN2020-PreProd_alias_name` etc.
- `crossEnvOneRowPerApi()` — gleiche API in DEV + PreProd → eine Datenzeile, env-spezifische Alias-Namen und URLs in den jeweiligen Spalten.
- `crossEnvEmptyColumnsForMissingEnvironment()` — kein Eintrag für PreProd → 10 leere Felder in der PreProd-Spaltengruppe.
- `writeReportsCreatesCrossEnvFile()` — `writeReports()` erzeugt eine Datei `report_all_environments_*.csv`, Rückgabewert = Env-Anzahl + 1.

**Todo List:**
1. In [`DbReportServiceTest.java`](app/src/test/java/com/agwcontrol/DbReportServiceTest.java) neuen Testblock `// Cross-Environment Report` nach dem bestehenden `writeReports`-Block hinzufügen.
2. `crossEnvHeaderContainsAllEnvironments()`: Zwei APIs in `DN2020-DEV` und `DN2020-PreProd` speichern, `buildCrossEnvCsv()` aufrufen, Header-Zeile auf `DN2020-DEV_alias_name`, `DN2020-DEV_endpoint_url`, `DN2020-PreProd_alias_name` prüfen.
3. `crossEnvOneRowPerApi()`: `Payments API v1` in DN2020-DEV (Alias `gw1`, URL `https://dev-gw1/pay`) und DN2020-PreProd (Alias `gw1`, URL `https://preprod-gw1/pay`) speichern + Check-Ergebnisse → genau 1 Datenzeile, beide URLs in korrekten Spalten vorhanden.
4. `crossEnvEmptyColumnsForMissingEnvironment()`: API nur in DN2020-DEV vorhanden, DN2020-PreProd leer → Zeile enthält 10 leere Felder für DN2020-PreProd.
5. `writeReportsCreatesCrossEnvFile()`: `writeReports()` aufrufen, prüfen dass eine Datei mit Prefix `report_all_environments_` existiert, Rückgabewert = Env-Anzahl + 1.

**Relevant Context:**
- Bestehende Test-Struktur in [`DbReportServiceTest.java`](app/src/test/java/com/agwcontrol/DbReportServiceTest.java:16) — gleiche `setUp()`-Methode mit In-Memory-DB.
- `db.saveApis()`, `db.saveEndpoints()`, `db.saveCheckResult()` stehen zur Verfügung.
- Bestehender Test [`writeReportsCreatesFilePerEnvironment()`](app/src/test/java/com/agwcontrol/DbReportServiceTest.java:179) als Vorlage für `writeReportsCreatesCrossEnvFile()`.
- Für Test 4 (`crossEnvEmptyColumnsForMissingEnvironment`): DN2020-PreProd muss als Umgebung in der DB bekannt sein (via `db.saveApis` mit einer anderen API), damit `loadEnvironments()` sie auch zurückgibt — sonst erscheint PreProd gar nicht im Header.

---

## Ausführungsreihenfolge

```
Sub-Task 1 → Sub-Task 2 → Sub-Task 3 → Sub-Task 4
```

Sub-Tasks 1–3 bauen aufeinander auf (Konstante → Methode → Integration). Sub-Task 4 (Tests) kann erst nach Sub-Task 2 + 3 sinnvoll ausgeführt werden.
