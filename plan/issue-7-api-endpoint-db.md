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

    /** Schaltet beide Flags gleichzeitig um (Session-Toggle). */
    public void toggleAll() {
        useDbForApis      = !useDbForApis;
        useDbForEndpoints = !useDbForEndpoints;
    }

    public String label() {
        return useDbForApis ? "DB" : "Server (neu laden)";
    }
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

### 4. `InteractiveMenu` – Cache-Toggle

**Cache-Status wird im Aktionsmenü-Header angezeigt**, `[c]` schaltet für die
aktuelle Session um (gilt für alle folgenden Aktionen).

**Normalfall – DB hat bereits Daten:**
```
Aktion für PROD (2 Server):  Cache-Modus: [Server (neu laden)]
  [1]  Ping
  [2]  TCP-Check
  [3]  APIs auflisten
  [4]  Endpoint-Check
  ─────────────────────────────────────
  [c]  Cache umschalten  →  würde wechseln zu: DB
  [b]  Zurück
  [q]  Beenden
```

**DB noch leer für diese Umgebung – Hinweis beim Toggle:**
```
Aktion für PROD (2 Server):  Cache-Modus: [Server (neu laden)]
  ...
  [c]  Cache umschalten  →  würde wechseln zu: DB  ⚠ noch keine Daten für PROD
```

- `InteractiveMenu` hält ein `DbCacheConfig cacheConfig`-Feld (Standard: `useDb = false`)
- `[c]` ruft `cacheConfig.toggleAll()` auf und druckt das Menü neu
- Aktionen `[3]` und `[4]` nutzen fortan die jeweils aktuelle `cacheConfig`

**Fallback-Logik in `AgwApiService` (Cache leer):**

Wenn `useDb = true`, die DB für die Umgebung aber **keine Daten enthält**:
- wird automatisch vom Server geladen (Fallback)
- Daten werden in die DB gespeichert
- Ausgabe im Menü: `[Cache leer – lade vom Server]`

```
Lade APIs für PROD ...  [Cache leer – lade vom Server]
```

Dies stellt sicher, dass der Nutzer nie mit einer leeren Ergebnisliste konfrontiert wird,
auch wenn er versehentlich `[DB]` gewählt hat bevor je Daten geladen wurden.

---

### 5. `build.gradle` – neue Dependency

```groovy
implementation 'org.xerial:sqlite-jdbc:3.45.3.0'
```

---

### 6. Tests

| Testklasse | Inhalt |
|---|---|
| `ApiDatabaseTest` | Schema-Init, saveApis/loadApis, saveEndpoints/loadEndpoints, Überschreiben |
| `AgwApiServiceTest` (erweitern) | Cache=DB gibt DB-Daten zurück; Cache=Server ruft Server auf und speichert; Cache=DB aber DB leer → Fallback auf Server |

Tests verwenden eine In-Memory-SQLite-DB (`:memory:`).

---

## Implementierungsreihenfolge

- [ ] `build.gradle`: sqlite-jdbc Dependency hinzufügen
- [ ] `DbCacheConfig` erstellen (inkl. `toggleAll()` und `label()`)
- [ ] `ApiDatabase` erstellen (inkl. Schema-Init, save/load für apis und endpoints)
- [ ] `ApiDatabaseTest` erstellen
- [ ] `AgwApiService` erweitern (neue überladene Methoden mit DB/Cache-Parameter)
- [ ] `AgwApiServiceTest` erweitern
- [ ] `InteractiveMenu` erweitern: `cacheConfig`-Feld, `[c]`-Toggle, Header-Anzeige
- [ ] `InteractiveMenuTest` erweitern: Toggle-Verhalten prüfen
- [ ] Build + alle Tests grün

---

## Nicht im Scope dieses Issues

- Vergleich zwischen zwei Umgebungen → Issue #8
