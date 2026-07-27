# Plan: Issue #5 – Endpoint-Check für AGW-APIs (korrigiert: Routing Policy / Endpoint Alias)

Branch: `feat/issue-5-endpoint-check`

---

## Ziel

Im interaktiven Menü wird eine neue Aktion **„Endpoint-Check"** ergänzt.
Für eine oder alle APIs eines gewählten Servers wird der **Routing-Endpoint** ermittelt
und angezeigt. Wenn die API eine Routing Policy mit einem **Endpoint-Alias** verwendet,
wird dieser Alias aufgelöst (`endPointURI`) und die aufgelöste URL auf Erreichbarkeit
geprüft.

Erweiterung des Aktionsmenüs:

```
Aktion für OH-DEV (3 Server):
  [1]  Ping
  [2]  TCP-Check
  [3]  APIs auflisten
  [4]  Endpoint-Check
  [b]  Zurück
  [q]  Beenden
```

Bei Auswahl `[4]`:
1. Server wählen (wie bei `[3]`)
2. API-Liste laden, Nutzer wählt eine API oder `[a]` für alle
3. Pro API: `nativeEndpoint`-Array auswerten – Alias-Flag erkennen
4. Bei `alias = true`: Alias über `GET /alias/{aliasId}` auflösen → `endPointURI`
5. HTTP-Check gegen die aufgelöste URL durchführen
6. Ergebnis tabellarisch ausgeben

---

## AGW REST-API (aus OpenAPI-Spec)

### Schritt 1 – API-Liste laden (bereits vorhanden)

```
GET <IS-URL>/rest/apigateway/apis?size=750
Authorization: Basic <base64(user:password)>
Accept: application/json
```

Wird über den bestehenden `AgwApiService.listApis()` aufgerufen.

---

### Schritt 2 – API-Detail abrufen: `nativeEndpoint`

```
GET <IS-URL>/rest/apigateway/apis/{apiId}
Authorization: Basic <base64(user:password)>
Accept: application/json
```

#### Response (relevanter Auszug)

```json
{
  "apiResponse": {
    "api": {
      "nativeEndpoint": [
        {
          "uri": "https://backend.example.com/service",
          "alias": false,
          "connectionTimeoutDuration": 0,
          "passSecurityHeaders": true
        }
      ]
    },
    "gatewayEndPoints": ["https://agw-host:443/gateway/CustomerAPI/v1"],
    "responseStatus": "SUCCESS"
  }
}
```

**Fall: Alias = true**

```json
{
  "apiResponse": {
    "api": {
      "nativeEndpoint": [
        {
          "uri": "MystageEndpoint",
          "alias": true,
          "connectionTimeoutDuration": 0,
          "passSecurityHeaders": true
        }
      ]
    }
  }
}
```

Wenn `"alias": true`, ist `"uri"` der **Name des Endpoint-Alias** (nicht die Ziel-URL).
Die echte URL muss über die Alias-API aufgelöst werden.

Relevante Felder aus `Endpoint`-Schema:

| JSON-Feld                    | Bedeutung                                                  |
|------------------------------|------------------------------------------------------------|
| `nativeEndpoint[].uri`       | URL oder Alias-Name (je nach `alias`-Flag)                 |
| `nativeEndpoint[].alias`     | `true` = `uri` ist ein Alias-Name; `false` = direkte URL  |
| `gatewayEndPoints`           | Gateway-seitige Endpunkte (bereits implementiert)          |

---

### Schritt 3 – Endpoint-Alias auflösen (nur wenn `alias = true`)

```
GET <IS-URL>/rest/apigateway/alias/{aliasId}
Authorization: Basic <base64(user:password)>
Accept: application/json
```

Wobei `{aliasId}` = der Wert aus `nativeEndpoint[].uri` (z.B. `"MystageEndpoint"`).

#### Response (relevanter Auszug)

```json
{
  "id": "732c4526-db9a-4ef9-9782-edda1a6aa9bc",
  "endPointURI": "https://myDevstage:9090",
  "name": "MystageEndpoint",
  "type": "endpoint"
}
```

Relevante Felder:

| JSON-Feld      | Bedeutung                                       |
|----------------|-------------------------------------------------|
| `endPointURI`  | Die aufgelöste Backend-URL des Endpoint-Alias   |
| `name`         | Alias-Name (zur Anzeige)                        |
| `type`         | Alias-Typ (`endpoint` für Endpoint-Aliasse)     |

---

### Schritt 4 – HTTP-Check gegen den Backend-Endpoint

Für jeden aufgelösten Endpoint wird ein HTTP-Request abgesetzt:

- Methode: `HEAD` (Fallback auf `GET` wenn HEAD 405 zurückliefert)
- Timeout: 10 s (Connect) + 15 s (Read)
- TLS: Trust-All SSLContext (wie in `AgwApiService`)
- Authentifizierung: keine (Backend-URL)
- Redirect folgen: ja (bis max. 5 Redirects)

---

## Aktueller Zustand (Ist)

| Klasse / Datei                    | Relevanz                                                    |
|-----------------------------------|-------------------------------------------------------------|
| `AgwApiService.java`              | Lädt API-Liste + `getEndpoints()` → **wird erweitert**      |
| `ApiInfo.java`                    | API-Datenmodell – **bleibt unverändert**                    |
| `EndpointCheckResult.java`        | Ergebnis-Modell – **bleibt unverändert**                    |
| `EndpointCheckService.java`       | HTTP HEAD/GET-Check – **bleibt unverändert**                |
| `EndpointCheckResultFormatter.java` | Ausgabe-Formatter – **wird erweitert** (Alias-Name anzeigen)|
| `InteractiveMenu.java`            | Aktionsmenü + `selectApis()` + `runEndpointCheck()` – **wird erweitert** |
| `ServerConfig.java`               | Server-Konfiguration – **bleibt unverändert**               |

**Was bereits implementiert ist:**
- `getEndpoints()` in `AgwApiService` → liest `gatewayEndPoints`-Array
- `EndpointCheckService.check()` → HTTP HEAD/GET
- `InteractiveMenu` → Option [4], `selectApis()`, `runEndpointCheck()`

**Was fehlt / falsch ist:**
- Die aktuelle Implementierung prüft `gatewayEndPoints` (die Gateway-seitige URL)
- Gefordert ist die Prüfung des **Routing-Endpoints** (Backend-seitige `nativeEndpoint`)
- Bei `alias = true` muss der Alias aufgelöst werden via `GET /alias/{aliasId}`
- Die API-Liste soll den **Routing-Endpoint-Namen** bereits beim Auflisten anzeigen

---

## Geplante Änderungen (Soll)

### 1. Neues Datenmodell: `RoutingEndpoint`

Kapselt den ermittelten Routing-Endpunkt einer API (vor dem HTTP-Check):

```java
public class RoutingEndpoint {
    private final String aliasName;    // null wenn kein Alias; sonst z.B. "MystageEndpoint"
    private final String resolvedUrl;  // aufgelöste URL (endPointURI oder direkte uri)
    private final boolean isAlias;     // true wenn über Alias-Auflösung

    // Konstruktor + Getter
}
```

---

### 2. Erweiterung `AgwApiService`

#### 2a. Neue Methode `getNativeEndpoints()`

Ruft `GET /rest/apigateway/apis/{apiId}` auf und extrahiert das `nativeEndpoint`-Array:

```java
/**
 * Gibt alle nativeEndpoint-Einträge der API zurück.
 * Jeder Eintrag enthält uri + alias-Flag.
 */
public List<RoutingEndpoint> getNativeEndpoints(ServerConfig server, String apiId) throws IOException
```

**Implementierungsdetails:**
- URL: `<resolveBaseUrl(server)>/rest/apigateway/apis/<apiId>`
- JSON-Parsing: Regex/Pattern – extrahiert `nativeEndpoint`-Blöcke, je Eintrag `uri` und `alias`-Flag
- Bei `alias = true`: Alias-Auflösung via `resolveAlias()` (s.u.)
- Gibt leere Liste zurück wenn kein `nativeEndpoint`-Feld vorhanden

#### 2b. Neue Methode `resolveAlias()`

Ruft `GET /rest/apigateway/alias/{aliasId}` auf und gibt die `endPointURI` zurück:

```java
/**
 * Löst einen Endpoint-Alias auf und gibt die endPointURI zurück.
 * Gibt null zurück wenn der Alias nicht vom Typ "endpoint" ist oder nicht gefunden.
 */
String resolveAlias(ServerConfig server, String aliasName) throws IOException
```

**Implementierungsdetails:**
- URL: `<resolveBaseUrl(server)>/rest/apigateway/alias/<aliasName>`
- Parst `"endPointURI"` aus der Response via Regex
- Gibt `null` zurück bei HTTP 404 oder fehlendem `endPointURI`-Feld

#### 2c. Bestehende Methode `getEndpoints()` – wird ersetzt

`getEndpoints()` wird durch `getNativeEndpoints()` ersetzt.
Die Methode `getEndpoints()` wird entfernt (oder als deprecated markiert, bis alle Aufrufer umgestellt sind).

---

### 3. Anzeige des Routing-Endpoints in der API-Liste

In `InteractiveMenu.selectApis()` wird beim Anzeigen der API-Liste zusätzlich der
Routing-Endpoint-Name angezeigt:

```
API auswählen für vm40757.linux.oebb.at:
  [1]  CustomerAPI          v1    REST    → MystageEndpoint (Alias)
  [2]  OrderService         v2    REST    → https://backend:8080/order (direkt)
  [3]  LegacyCalcService    10.3  SOAP    → (kein Endpoint)
  [a]  Alle APIs
  [b]  Zurück
Auswahl:
```

Dazu wird pro API ein zusätzlicher Aufruf `getNativeEndpoints()` gemacht,
um den ersten Endpoint-Eintrag für die Anzeige zu ermitteln.

> **Hinweis:** Dieser zusätzliche Aufruf macht die API-Liste langsamer.
> Die Anzeige kann optional mit einer Ladezeile `"Lade Routing-Endpoints ..."` versehen werden.

---

### 4. Schritt 2 im Endpoint-Check: Alias prüfen

In `InteractiveMenu.runEndpointCheck()` wird der Ablauf angepasst:

1. `getNativeEndpoints(server, api.getId())` aufrufen
2. Pro `RoutingEndpoint`:
   - Wenn `isAlias = true`: Alias-Name anzeigen + aufgelöste URL für den Check verwenden
   - Wenn `isAlias = false`: direkte URL für den Check verwenden
3. `endpointCheckService.check(apiName, apiVersion, resolvedUrl)` aufrufen
4. Ergebnis ausgeben

---

### 5. Erweiterung `EndpointCheckResultFormatter`

Der Formatter zeigt zusätzlich den Alias-Namen an, wenn vorhanden:

```
Endpoint-Check für CustomerAPI v1 auf vm40757.linux.oebb.at
─────────────────────────────────────────────────────────────────────────────
  Routing Endpoint         Alias          URL                      Status  OK
  ─────────────────────────────────────────────────────────────────────────
  MystageEndpoint          (Alias)        https://myDevstage:9090  200     JA
  https://backend:8080     (direkt)       https://backend:8080       0     NEIN (timeout)
─────────────────────────────────────────────────────────────────────────────
  2 Endpoints geprüft, 1 erreichbar
```

Anpassung von `EndpointCheckResult`: Feld `aliasName` (nullable) hinzufügen.

---

### 6. Tests

| Test-Klasse                             | Inhalt                                                                                      |
|-----------------------------------------|---------------------------------------------------------------------------------------------|
| `AgwApiServiceTest.java`                | `parseNativeEndpoints()` mit alias=false; alias=true; `resolveAlias()` mit gültigem/nicht gefundenem Alias |
| `EndpointCheckResultTest.java`          | Erweiterung: `aliasName`-Feld                                                               |
| `EndpointCheckResultFormatterTest.java` | Alias-Anzeige in der Tabelle                                                                |
| `InteractiveMenuTest.java`              | `[4]` → Routing-Endpoint-Anzeige in API-Liste; Alias wird aufgelöst und geprüft            |

---

## Tabellenausgabe – Beispiele

**Mit Alias:**
```
Endpoint-Check für CustomerAPI v1 auf vm40757.linux.oebb.at
─────────────────────────────────────────────────────────────────────────
  Alias / Endpoint              URL                           Status  OK
  ───────────────────────────────────────────────────────────────────────
  MystageEndpoint (Alias)       https://myDevstage:9090       200     JA
─────────────────────────────────────────────────────────────────────────
  1 Endpoint geprüft, 1 erreichbar
```

**Direkte URL:**
```
Endpoint-Check für OrderService v2 auf vm40757.linux.oebb.at
─────────────────────────────────────────────────────────────────────────
  Alias / Endpoint              URL                           Status  OK
  ───────────────────────────────────────────────────────────────────────
  https://backend:8080/order    (direkt)                      200     JA
─────────────────────────────────────────────────────────────────────────
  1 Endpoint geprüft, 1 erreichbar
```

**Alle APIs:**
```
Endpoint-Check für alle APIs auf vm40757.linux.oebb.at
────────────────────────────────────────────────────────────────────────────────────────────
  API                     Alias / Endpoint             URL                        Status  OK
  ────────────────────────────────────────────────────────────────────────────────────────
  CustomerAPI v1          MystageEndpoint (Alias)      https://myDevstage:9090    200     JA
  OrderService v2         https://backend:8080/order   (direkt)                   404     NEIN
────────────────────────────────────────────────────────────────────────────────────────────
  2 Endpoints geprüft, 1 erreichbar
```

---

## Reihenfolge der Implementierung

1. [x] Neues Datenmodell `RoutingEndpoint` anlegen
2. [x] `AgwApiService` erweitern: `getNativeEndpoints()` + `resolveAlias()`
3. [x] Bestehende `getEndpoints()`-Methode entfernen und Aufrufer umstellen
4. [x] `EndpointCheckResult` erweitern: Feld `aliasName` (nullable)
5. [x] `EndpointCheckResultFormatter` anpassen: Alias-Spalte
6. [x] `InteractiveMenu.selectApis()` anpassen: Routing-Endpoint-Name in API-Liste anzeigen
7. [x] `InteractiveMenu.runEndpointCheck()` anpassen: Alias-Auflösung nutzen
8. [x] Tests anpassen: `AgwApiServiceTest`, `EndpointCheckResultFormatter`, `InteractiveMenuTest`
9. [x] Build + alle Tests grün (`./gradlew test`)
10. [ ] Manueller Test mit echtem Server

---

## Nicht im Scope dieses Issues

- Andere Alias-Typen als `endpoint` (z.B. `httpTransportSecurityAlias`)
- Paginierung bei > 750 APIs in der Auswahlliste
- Parallelisierung der Endpoint-Checks
- Farb-Ausgabe / ANSI-Codes
- Persistierung von Ergebnissen
- Authentifizierung beim HTTP-Check
