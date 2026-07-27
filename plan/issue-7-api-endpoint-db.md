# Plan: Issue #7 – APIs und Endpoints pro Umgebung persistieren

Branch: `feature/7-api-endpoint-db`

---

## Ziel

Eine SQLite-basierte lokale Datenbank (Single-File), in der API-Listen und Endpoints
pro Umgebung gespeichert werden können. Jede Ladefunktion ist mit einem Cache-Flag
steuerbar (DB vs. Server).

---

## Datenbankschema

**Datei:** `agwcontrol.db` (im Arbeitsverzeichnis, via `--db-path` überschreibbar)

```sql
CREATE TABLE IF NOT EXISTS apis (
    environment  TEXT NOT NULL,
    api_id       TEXT NOT NULL,
    api_name     TEXT,
    api_version  TEXT,
    api_type     TEXT,
    is_active    INTEGER,          -- 0 / 1
    loaded_at    TEXT NOT NULL,    -- ISO-8601
    PRIMARY KEY (environment, api_id)
);

CREATE TABLE IF NOT EXISTS endpoints (
    environment  TEXT NOT NULL,
    api_id       TEXT NOT NULL,
    alias_name   TEXT,             -- null wenn direkter Endpoint
    resolved_url TEXT,
    is_alias     INTEGER,          -- 0 / 1
    loaded_at    TEXT NOT NULL,
    PRIMARY KEY (environment, api_id, alias_name, resolved_url)
);
```

---

## Neue Klassen / Änderungen

### 1. `ApiDatabase` (neu)
**Datei:** `app/src/main/java/com/agwcontrol/ApiDatabase.java`

Verantwortlich für alle DB-Operationen (SQLite via `org.xerial:sqlite-jdbc`).

```
ApiDatabase(String dbPath)
  void initSchema()

  void saveApis(String environment, List<ApiInfo> apis)
  List<ApiInfo> loadApis(String environment)          // leer wenn keine Daten

  void saveEndpoints(String environment, String apiId, List<RoutingEndpoint> endpoints)
  List<RoutingEndpoint> loadEndpoints(String environment, String apiId)
```

- `saveApis` / `saveEndpoints` löschen zuerst alle vorhandenen Einträge der
  jeweiligen Umgebung/API (INSERT OR REPLACE via PRIMARY KEY).
- `loadApis` gibt eine leere Liste zurück wenn keine DB-Einträge vorhanden.

---

### 2. `DbCacheConfig` (neu)
**Datei:** `app/src/main/java/com/agwcontrol/DbCacheConfig.java`

Einfaches Konfigurationsobjekt mit einem Flag pro Funktion.

```java
public class DbCacheConfig {
    boolean useDbForApis;       // true = aus DB, false = vom Server (+ DB überschreiben)
    boolean useDbForEndpoints;  // true = aus DB, false = vom Server (+ DB überschreiben)
}
```

---

### 3. `AgwApiService` – Erweiterung
**Datei:** `app/src/main/java/com/agwcontrol/AgwApiService.java`

Zwei neue Methoden, die `DbCacheConfig` und `ApiDatabase` nutzen:

```java
List<ApiInfo> listApis(ServerConfig server, String environment,
                       ApiDatabase db, DbCacheConfig cache)

List<RoutingEndpoint> getNativeEndpoints(ServerConfig server, String apiId,
                                         String environment,
                                         ApiDatabase db, DbCacheConfig cache)
```

Logik:
```
if (cache.useDbForApis && db.loadApis(environment) nicht leer)
    → return db.loadApis(environment)
else
    → vom Server laden, db.saveApis(environment, result), return result
```

Die bestehenden Methoden ohne DB-Parameter bleiben unverändert erhalten
(Abwärtskompatibilität).

---

### 4. `build.gradle` – neue Dependency

```groovy
implementation 'org.xerial:sqlite-jdbc:3.45.3.0'
```

---

### 5. Tests

| Testklasse | Inhalt |
|---|---|
| `ApiDatabaseTest` | Schema-Init, saveApis/loadApis, saveEndpoints/loadEndpoints, Überschreiben |
| `AgwApiServiceTest` (erweitern) | Cache=DB gibt DB-Daten zurück; Cache=Server ruft Server auf und speichert |

Tests verwenden eine In-Memory-SQLite-DB (`:memory:`).

---

## Implementierungsreihenfolge

- [ ] `build.gradle`: sqlite-jdbc Dependency hinzufügen
- [ ] `DbCacheConfig` erstellen
- [ ] `ApiDatabase` erstellen (inkl. Schema-Init, save/load für apis und endpoints)
- [ ] `ApiDatabaseTest` erstellen
- [ ] `AgwApiService` erweitern (neue überladene Methoden mit DB/Cache-Parameter)
- [ ] `AgwApiServiceTest` erweitern
- [ ] Build + alle Tests grün

---

## Nicht im Scope dieses Issues

- Vergleich zwischen zwei Umgebungen → Issue #8
- CLI-Integration / Menü-Anpassung für Cache-Steuerung
