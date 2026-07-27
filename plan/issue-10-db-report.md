# Plan: Issue #10 – Report aus DB: APIs mit Endpoints pro Umgebung ausgeben

Branch: `feature/10-db-report`

---

## Ziel

Aus der lokalen SQLite-Datenbank (`agwcontrol.db`) einen strukturierten **Report** erzeugen,
der pro Umgebung alle gespeicherten APIs mit ihren Endpoints auflistet.
Der Report ist sowohl auf der Konsole ausgabbar als auch optional in eine Datei exportierbar.

---

## Abhängigkeiten

- Issue #7 (`ApiDatabase`, `ApiInfo`, `RoutingEndpoint`) muss abgeschlossen sein ✅

---

## Gewünschte Ausgabe (Beispiel)

```
Umgebung: PROD
──────────────────────────────────────────────────────────────────────
  API: Kundendaten-API  v1.0  [REST]  aktiv
    Endpoint:  MystageEndpoint (Alias)  →  https://backend.example.com/kunden
    Endpoint:  (direkt)                 →  https://backend.example.com/kunden/v2

  API: Auftrags-API  v2.1  [REST]  aktiv
    Endpoint:  AuftragAlias (Alias)     →  https://auftraege.internal.at/api

Umgebung: TEST
──────────────────────────────────────────────────────────────────────
  API: Kundendaten-API  v1.0  [REST]  aktiv
    Endpoint:  TestEndpoint (Alias)     →  https://test.backend.example.com/kunden

Gesamt: 2 Umgebung(en), 3 API(s), 4 Endpoint(s)
```

---

## Neue Klassen / Änderungen

### 1. `DbReportService` (neu)
**Datei:** `app/src/main/java/com/agwcontrol/DbReportService.java`

Liest alle Umgebungen, APIs und Endpoints aus der DB und erzeugt eine
formatierte Ausgabe.

```java
public class DbReportService {

    DbReportService(ApiDatabase db)

    /** Vollständiger Report als String (für Tests und Dateiexport). */
    String buildReport() throws SQLException

    /** Gibt den Report auf den übergebenen PrintStream aus (z. B. System.out). */
    void printReport(PrintStream out) throws SQLException
}
```

**Interne Logik von `buildReport()`:**

1. `SELECT DISTINCT environment FROM apis ORDER BY environment`  
   → alle bekannten Umgebungen ermitteln  
   *(neue Methode `loadEnvironments()` in `ApiDatabase`)*
2. Pro Umgebung: `db.loadApis(environment)` → Liste der APIs
3. Pro API: `db.loadEndpoints(environment, api.getId())` → Liste der Endpoints
4. Formatierung:
   - Umgebungs-Header mit Trennlinie
   - Eingerückte API-Zeile mit Name, Version, Typ, Status
   - Doppelt eingerückte Endpoint-Zeilen mit Label und aufgelöster URL
   - Wenn keine APIs vorhanden: `(keine Daten)`
   - Fußzeile mit Gesamtzählung

---

### 2. `ApiDatabase` – neue Methode `loadEnvironments()`
**Datei:** `app/src/main/java/com/agwcontrol/ApiDatabase.java`

```java
/**
 * Gibt alle Umgebungen zurück, für die APIs in der DB gespeichert sind.
 * Sortiert alphabetisch.
 */
public List<String> loadEnvironments() throws SQLException
```

SQL:
```sql
SELECT DISTINCT environment FROM apis ORDER BY environment
```

---

### 3. `App` – neuer Unterbefehl `report`
**Datei:** `app/src/main/java/com/agwcontrol/App.java`

Neuer Zweig in `main()`:

```bash
java -jar agwcontrol.jar report [--db-path <pfad>] [--output <datei>]
```

| Flag | Default | Bedeutung |
|---|---|---|
| `--db-path` | `agwcontrol.db` | Pfad zur SQLite-DB |
| `--output`  | *(keiner)*      | Schreibt Report in Datei statt auf stdout |

Ablauf in `main()`:
1. Subkommando `report` erkennen
2. `--db-path` und `--output` aus Args auslesen
3. `ApiDatabase` mit `db-path` öffnen + `initSchema()`
4. `new DbReportService(db).printReport(out)` aufrufen
5. Bei `--output`: `out` = `PrintStream` auf Datei; sonst `System.out`

---

## Tests

### `ApiDatabaseTest` – Erweiterung
Neue Testmethode:

| Methode | Prüft |
|---|---|
| `loadEnvironmentsReturnsDistinctSorted()` | Gibt alle gespeicherten Umgebungen alphabetisch sortiert zurück |
| `loadEnvironmentsEmptyWhenNoData()` | Leere Liste wenn DB leer |

### `DbReportServiceTest` (neu)
**Datei:** `app/src/test/java/com/agwcontrol/DbReportServiceTest.java`

Alle Tests nutzen In-Memory-SQLite (`:memory:`).

| Testmethode | Prüft |
|---|---|
| `reportContainsEnvironmentHeader()` | Umgebungsname erscheint im Report |
| `reportContainsApiNameAndVersion()` | API-Name + Version erscheinen korrekt formatiert |
| `reportContainsEndpointRow()` | Endpoint-Zeile mit URL erscheint |
| `reportShowsAliasLabel()` | Alias-Name wird mit `(Alias)` gekennzeichnet |
| `reportShowsDirectEndpoint()` | Direkter Endpoint erscheint als `(direkt)` |
| `reportMultipleEnvironments()` | Mehrere Umgebungen werden alle ausgegeben |
| `reportEmptyDatabase()` | Leere DB → `(keine Daten)` erscheint |
| `reportFooterContainsTotals()` | Fußzeile enthält Gesamt-Zählung |

---

## Implementierungsreihenfolge

- [ ] `ApiDatabase`: Methode `loadEnvironments()` hinzufügen
- [ ] `ApiDatabaseTest`: Tests für `loadEnvironments()` ergänzen
- [ ] `DbReportService` erstellen (inkl. `buildReport()` und `printReport()`)
- [ ] `DbReportServiceTest` erstellen
- [ ] `App`: Unterbefehl `report` mit `--db-path` und `--output` hinzufügen
- [ ] Build + alle Tests grün

---

## Nicht im Scope dieses Issues

- HTML- oder JSON-Export
- Filterung nach Umgebung via Flag
- Vergleich zwischen zwei Umgebungen (→ Issue #8)
