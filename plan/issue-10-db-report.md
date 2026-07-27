# Plan: Issue #10 – Report aus DB: CSV-Dateien pro Umgebung

Branch: `feature/10-db-report`

---

## Ziel

Aus der lokalen SQLite-Datenbank (`agwcontrol.db`) pro Umgebung eine **CSV-Datei** erzeugen,
die alle APIs mit ihren Endpoints und den jeweils letzten Endpoint-Check-Ergebnissen
(Ping, TCP, HTTP Status) auflistet.

Dazu müssen Check-Ergebnisse erstmals in der DB persistiert werden.

---

## Abhängigkeiten

- Issue #7 (`ApiDatabase`, `ApiInfo`, `RoutingEndpoint`) muss abgeschlossen sein ✅

---

## Datenbankschema – neue Tabelle `endpoint_check_results`

```sql
CREATE TABLE IF NOT EXISTS endpoint_check_results (
    environment  TEXT NOT NULL,
    api_id       TEXT NOT NULL,
    server_host  TEXT NOT NULL,       -- AGW-Server, von dem der Check ausgeführt wurde
    resolved_url TEXT NOT NULL,       -- geprüfte Backend-URL
    alias_name   TEXT,                -- Alias-Name, null wenn direkter Endpoint
    ping_ok      INTEGER NOT NULL,    -- 0 / 1
    ping_ms      INTEGER NOT NULL,    -- -1 wenn nicht verfügbar
    tcp_ok       INTEGER NOT NULL,    -- 0 / 1
    tcp_ms       INTEGER NOT NULL,    -- -1 wenn nicht verfügbar
    http_status  INTEGER NOT NULL,    -- 0 wenn Fehler
    reachable    INTEGER NOT NULL,    -- 0 / 1
    error_msg    TEXT,
    checked_at   TEXT NOT NULL,       -- ISO-8601
    PRIMARY KEY (environment, api_id, server_host, resolved_url)
);
```

Der Primary Key stellt sicher, dass pro Kombination aus Umgebung + API + Server + URL
immer nur das **neueste** Ergebnis gespeichert bleibt (`INSERT OR REPLACE`).

---

## Ausgabe-Format

Pro Umgebung wird eine separate CSV-Datei erstellt:

**Dateiname:** `report_<environment>_<timestamp>.csv`
Format: `report_PROD_20240601_100000.csv`
Timestamp: Zeitpunkt der Report-Erstellung, Format `yyyyMMdd_HHmmss`
**Encoding:** UTF-8
**Trennzeichen:** Semikolon (`;`)
**Felder mit Sonderzeichen** werden in doppelte Anführungszeichen eingeschlossen.

### CSV-Spalten

```
api_name;api_version;api_type;api_active;alias_name;endpoint_url;server_host;ping_ok;ping_ms;tcp_ok;tcp_ms;http_status;reachable;error_msg;checked_at
```

| Spalte | Quelle | Beispiel |
|---|---|---|
| `api_name` | `apis.api_name` | `Kundendaten-API` |
| `api_version` | `apis.api_version` | `v1.0` |
| `api_type` | `apis.api_type` | `REST` |
| `api_active` | `apis.is_active` | `true` |
| `alias_name` | `endpoints.alias_name` | `MystageEndpoint` *(leer wenn direkt)* |
| `endpoint_url` | `endpoints.resolved_url` | `https://backend.example.com/kunden` |
| `server_host` | `endpoint_check_results.server_host` | `vm30073` *(leer wenn kein Check)* |
| `ping_ok` | `endpoint_check_results.ping_ok` | `true` *(leer wenn kein Check)* |
| `ping_ms` | `endpoint_check_results.ping_ms` | `12` *(leer wenn kein Check)* |
| `tcp_ok` | `endpoint_check_results.tcp_ok` | `true` *(leer wenn kein Check)* |
| `tcp_ms` | `endpoint_check_results.tcp_ms` | `8` *(leer wenn kein Check)* |
| `http_status` | `endpoint_check_results.http_status` | `200` *(leer wenn kein Check)* |
| `reachable` | `endpoint_check_results.reachable` | `true` *(leer wenn kein Check)* |
| `error_msg` | `endpoint_check_results.error_msg` | *(leer wenn OK)* |
| `checked_at` | `endpoint_check_results.checked_at` | `2024-06-01T10:00:00Z` *(leer wenn kein Check)* |

### Beispiel `report_PROD.csv`

```
api_name;api_version;api_type;api_active;alias_name;endpoint_url;server_host;ping_ok;ping_ms;tcp_ok;tcp_ms;http_status;reachable;error_msg;checked_at
Kundendaten-API;v1.0;REST;true;MystageEndpoint;https://backend.example.com/kunden;vm30073;true;12;true;8;200;true;;2024-06-01T10:00:00Z
Kundendaten-API;v1.0;REST;true;MystageEndpoint;https://backend.example.com/kunden;vm30074;false;-1;false;-1;0;false;;2024-06-01T10:00:01Z
Kundendaten-API;v1.0;REST;true;;https://backend.example.com/kunden/v2;vm30073;true;9;true;6;200;true;;2024-06-01T10:00:02Z
Auftrags-API;v2.1;REST;true;AuftragAlias;https://auftraege.internal.at/api;vm30073;true;15;true;10;201;true;;2024-06-01T10:00:03Z
Geosphere_Dataset_API;v1;REST;true;;https://dataset.api.hub.geosphere.at/v1/;vm30073;false;-1;true;0;0;false;"Remote host terminated the handshake";2024-06-01T10:00:04Z
HIM_SX_IN_DB_API;1.0.0;REST;true;;dummy.dummy;vm30073;false;-1;false;-1;0;false;"Ungültige URL: no protocol: dummy.dummy";2024-06-01T10:00:05Z
GKB-ASSET_API;2.0;REST;true;;http://localhost:8000;vm30073;true;0;false;-1;0;false;"Connection refused: connect";2024-06-01T10:00:06Z
GKB-ASSET_API;2.0;REST;true;;http://il-dev.3binfra.int;vm30073;false;-1;false;-1;0;false;"il-dev.3binfra.int";2024-06-01T10:00:07Z
```

> Endpoints ohne gespeicherte Check-Ergebnisse erscheinen trotzdem als Zeile –
> die Check-Spalten (`server_host` bis `checked_at`) bleiben dann leer.

**Fehlerszenarien im Überblick:**

| Szenario | ping_ok | ping_ms | tcp_ok | tcp_ms | http_status | error_msg |
|---|---|---|---|---|---|---|
| TLS-Handshake-Fehler | `false` | `-1` | `true` | `0` | `0` | `Remote host terminated the handshake` |
| Ungültige URL / kein Protokoll | `false` | `-1` | `false` | `-1` | `0` | `Ungültige URL: no protocol: ...` |
| Localhost / Port nicht offen | `true` | `0` | `false` | `-1` | `0` | `Connection refused: connect` |
| Host nicht erreichbar | `false` | `-1` | `false` | `-1` | `0` | Hostname (z. B. `il-dev.3binfra.int`) |

---

## Neue Klassen / Änderungen

### 1. `ApiDatabase` – Erweiterungen
**Datei:** `app/src/main/java/com/agwcontrol/ApiDatabase.java`

#### Neue Methode `loadEnvironments()`
```java
/**
 * Gibt alle Umgebungen zurück, für die APIs in der DB gespeichert sind.
 * Sortiert alphabetisch.
 */
public List<String> loadEnvironments() throws SQLException
```
SQL: `SELECT DISTINCT environment FROM apis ORDER BY environment`

#### Neue Methode `saveCheckResult()`
```java
/**
 * Speichert ein Endpoint-Check-Ergebnis.
 * Vorhandener Eintrag für dieselbe Kombination (environment, api_id,
 * server_host, resolved_url) wird überschrieben (INSERT OR REPLACE).
 */
public void saveCheckResult(String environment, String apiId,
                            String serverHost, EndpointCheckResult result) throws SQLException
```

#### Neue Methode `loadCheckResults()`
```java
/**
 * Lädt alle gespeicherten Check-Ergebnisse für eine Umgebung und API.
 * Gibt eine leere Liste zurück wenn keine Daten vorhanden.
 */
public List<EndpointCheckResult> loadCheckResults(String environment,
                                                   String apiId) throws SQLException
```

---

### 2. `EndpointCheckService` – DB-Persistierung
**Datei:** `app/src/main/java/com/agwcontrol/EndpointCheckService.java`

Neue überladene Methode, die nach dem Check das Ergebnis in die DB schreibt:

```java
public EndpointCheckResult check(String apiName, String apiVersion,
                                  String aliasName, String urlStr,
                                  String environment, String apiId, String serverHost,
                                  ApiDatabase db) throws SQLException
```

Logik: bestehende `check()`-Logik ausführen, dann `db.saveCheckResult(...)` aufrufen.  
Die bestehende `check()`-Methode ohne DB-Parameter bleibt unverändert (Abwärtskompatibilität).

---

### 3. `DbReportService` (neu)
**Datei:** `app/src/main/java/com/agwcontrol/DbReportService.java`

Liest alle Umgebungen, APIs, Endpoints und Check-Ergebnisse aus der DB und schreibt
pro Umgebung eine CSV-Datei.

```java
public class DbReportService {

    DbReportService(ApiDatabase db)

    /**
     * Erstellt pro Umgebung eine CSV-Datei im angegebenen Verzeichnis.
     * Dateiname: report_<environment>_<yyyyMMdd_HHmmss>.csv
     * Der Timestamp wird einmalig beim Aufruf gesetzt (gleich für alle Dateien).
     * Gibt die Anzahl der erstellten Dateien zurück.
     */
    int writeReports(Path outputDir) throws SQLException, IOException

    /**
     * Erstellt den CSV-Inhalt für eine einzelne Umgebung als String.
     * (Für Tests.)
     */
    String buildCsv(String environment) throws SQLException
}
```

**Interne Logik von `buildCsv(environment)`:**

1. `db.loadApis(environment)` → APIs
2. Pro API: `db.loadEndpoints(environment, api.getId())` → Endpoints
3. Pro API: `db.loadCheckResults(environment, api.getId())` → Check-Ergebnisse,
   gruppiert nach `resolved_url` → Map<url, List<CheckResult>>
4. Pro Endpoint: eine Zeile pro gespeichertem Check-Ergebnis (pro Server);
   kein Check vorhanden → eine Zeile mit leeren Check-Spalten
5. Header-Zeile + Datenzeilen, Semikolon-getrennt, Sonderzeichen in `"…"` einschließen

---

### 4. `App` – neuer Unterbefehl `report`
**Datei:** `app/src/main/java/com/agwcontrol/App.java`

```bash
java -jar agwcontrol.jar report [--db-path <pfad>] [--output-dir <verzeichnis>]
```

| Flag | Default | Bedeutung |
|---|---|---|
| `--db-path`    | `agwcontrol.db` | Pfad zur SQLite-DB |
| `--output-dir` | `.` (aktuelles Verzeichnis) | Zielverzeichnis für die CSV-Dateien |

Ablauf:
1. Subkommando `report` erkennen
2. `--db-path` und `--output-dir` aus Args auslesen
3. `ApiDatabase` mit `db-path` öffnen + `initSchema()`
4. `new DbReportService(db).writeReports(outputDir)` aufrufen
5. Für jede erstellte Datei eine Bestätigungszeile ausgeben:
   `Erstellt: report_PROD_20240601_100000.csv (4 Zeilen)`

---

## Tests

### `ApiDatabaseTest` – Erweiterung

| Testmethode | Prüft |
|---|---|
| `loadEnvironmentsReturnsDistinctSorted()` | Alle Umgebungen alphabetisch sortiert |
| `loadEnvironmentsEmptyWhenNoData()` | Leere Liste wenn DB leer |
| `saveAndLoadCheckResult()` | Gespeichertes Ergebnis wird vollständig zurückgelesen |
| `saveCheckResultOverwritesExisting()` | INSERT OR REPLACE: neueres Ergebnis ersetzt älteres |
| `loadCheckResultsEmptyWhenNoData()` | Leere Liste wenn keine Checks vorhanden |

### `DbReportServiceTest` (neu)
**Datei:** `app/src/test/java/com/agwcontrol/DbReportServiceTest.java`

Alle Tests nutzen In-Memory-SQLite (`:memory:`) und `buildCsv(environment)`.

| Testmethode | Prüft |
|---|---|
| `csvContainsHeaderRow()` | Erste Zeile enthält alle Spaltenbezeichner |
| `csvContainsApiColumns()` | API-Name, Version, Typ, aktiv erscheinen korrekt |
| `csvContainsEndpointUrl()` | `endpoint_url` erscheint in der Zeile |
| `csvAliasNamePresentForAliasEndpoint()` | `alias_name` befüllt wenn Alias |
| `csvAliasNameEmptyForDirectEndpoint()` | `alias_name` leer wenn direkter Endpoint |
| `csvContainsCheckResultColumns()` | Ping/TCP/HTTP-Spalten korrekt befüllt |
| `csvEmptyCheckColumnsWhenNoCheckStored()` | Check-Spalten leer wenn kein Ergebnis |
| `csvOneRowPerServerPerEndpoint()` | Mehrere Server → mehrere Zeilen für denselben Endpoint |
| `csvEmptyDatabase()` | Leere DB → nur Header-Zeile |
| `writeReportsCreatesFilePerEnvironment()` | Pro Umgebung eine Datei im Zielverzeichnis |

---

## Implementierungsreihenfolge

- [ ] `ApiDatabase`: Tabelle `endpoint_check_results` in `initSchema()` ergänzen
- [ ] `ApiDatabase`: `saveCheckResult()` und `loadCheckResults()` implementieren
- [ ] `ApiDatabase`: `loadEnvironments()` implementieren
- [ ] `ApiDatabaseTest`: alle neuen Tests ergänzen
- [ ] `EndpointCheckService`: überladene `check()`-Methode mit DB-Persistierung
- [ ] `DbReportService` erstellen (inkl. `buildCsv()` und `writeReports()`)
- [ ] `DbReportServiceTest` erstellen
- [ ] `App`: Unterbefehl `report` mit `--db-path` und `--output-dir` hinzufügen
- [ ] Build + alle Tests grün

---

## Nicht im Scope dieses Issues

- Filterung nach Umgebung via Flag
- Vergleich zwischen zwei Umgebungen (→ Issue #8)
