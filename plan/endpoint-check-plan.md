# Plan: Issue #5 – Endpoint-Check für AGW-APIs

Branch: `feat/issue-5-endpoint-check`

---

## Ziel

Im interaktiven Menü wird eine neue Aktion **„Endpoint-Check"** ergänzt.
Für eine oder alle APIs eines gewählten Servers werden deren Gateway-Endpoints
per HTTP-GET abgerufen und anschließend ein HTTP-Erreichbarkeits-Check
(HEAD oder GET) gegen jeden Endpoint durchgeführt. Das Ergebnis wird als Tabelle
auf der Konsole ausgegeben.

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

Bei Auswahl `[4]` wird zunächst ein Server gewählt (wie bei `[3]`),
dann die API-Liste geladen. Der Nutzer kann entweder **eine einzelne API**
oder **alle APIs** (`[a]`) auswählen. Anschließend werden die Gateway-Endpoints
der gewählten API(s) abgefragt und geprüft.

---

## AGW REST-API (aus OpenAPI-Spec `apis.openapi.json`)

### Schritt 1 – API-Liste laden (bereits vorhanden)

```
GET <IS-URL>/rest/apigateway/apis?size=750
Authorization: Basic <base64(user:password)>
Accept: application/json
```

Wird über den bestehenden `AgwApiService.listApis()` aufgerufen.

---

### Schritt 2 – Gateway-Endpoints einer API abrufen

Der Endpoint `GET /apis/{apiId}` liefert in der Response das Feld
`gatewayEndPoints` (Array von Strings) innerhalb des `apiResponse`-Objekts.

```
GET <IS-URL>/rest/apigateway/apis/{apiId}
Authorization: Basic <base64(user:password)>
Accept: application/json
```

#### Response-Struktur (relevanter Auszug)

```json
{
  "apiResponse": {
    "api": { ... },
    "gatewayEndPoints": [
      "https://agw-host:443/gateway/CustomerAPI/v1",
      "http://agw-host:5555/gateway/CustomerAPI/v1"
    ],
    "responseStatus": "SUCCESS"
  }
}
```

Relevante Felder aus `APIResponse`:

| JSON-Feld                     | Bedeutung                                    |
|-------------------------------|----------------------------------------------|
| `apiResponse.gatewayEndPoints` | Liste der Gateway-Endpoint-URLs (Strings)   |
| `apiResponse.responseStatus`  | `SUCCESS` / `ERROR` / `NOT_FOUND`           |

---

### Schritt 3 – HTTP-Check gegen jeden Endpoint

Für jeden Gateway-Endpoint wird ein HTTP-Request abgesetzt:

- Methode: `HEAD` (Fallback auf `GET` wenn HEAD 405 zurückliefert)
- Timeout: 10 s (Connect) + 15 s (Read)
- TLS: Trust-All SSLContext (wie in `AgwApiService`)
- Keine Authentifizierung (Endpoints sind für Consumers gedacht)
- Redirect folgen: ja (bis max. 5 Redirects)

Ergebnis je Endpoint:

| Feld         | Typ     | Beschreibung                                     |
|--------------|---------|--------------------------------------------------|
| `apiName`    | String  | Name der zugehörigen API                         |
| `apiVersion` | String  | Version der zugehörigen API                      |
| `url`        | String  | Die geprüfte Endpoint-URL                        |
| `httpStatus` | int     | HTTP-Statuscode (0 = nicht erreichbar)           |
| `reachable`  | boolean | true wenn HTTP 2xx oder 3xx                      |
| `errorMsg`   | String  | Fehlermeldung bei Exception, sonst leer          |

---

## Aktueller Zustand (Ist)

| Klasse / Datei               | Relevanz                                          |
|------------------------------|---------------------------------------------------|
| `AgwApiService.java`         | Lädt API-Liste – **wird erweitert** um `getEndpoints()` |
| `ApiInfo.java`               | API-Datenmodell – **bleibt unverändert**          |
| `InteractiveMenu.java`       | Aktionsmenü – **wird erweitert** (Option [4])     |
| `ServerConfig.java`          | Server-Konfiguration – **bleibt unverändert**     |
| `PingService.java`           | Ping-Logik – **bleibt unverändert**               |
| `TcpCheckService.java`       | TCP-Logik – **bleibt unverändert** (Muster für neuen Service) |

---

## Geplante Änderungen (Soll)

### 1. Neues Datenmodell: `EndpointCheckResult`

Kapselt das Ergebnis eines HTTP-Checks gegen einen Gateway-Endpoint.
Trägt zusätzlich den API-Namen und die Version, damit bei der Ausgabe über
mehrere APIs hinweg die Zuordnung erhalten bleibt:

```java
public class EndpointCheckResult {
    private final String apiName;
    private final String apiVersion;
    private final String url;
    private final int httpStatus;
    private final boolean reachable;
    private final String errorMsg;

    public EndpointCheckResult(String apiName, String apiVersion,
                               String url, int httpStatus,
                               boolean reachable, String errorMsg) { ... }

    // Getter
    public String getApiName()    { ... }
    public String getApiVersion() { ... }
    public String getUrl()        { ... }
    public int getHttpStatus()    { ... }
    public boolean isReachable()  { ... }
    public String getErrorMsg()   { ... }
}
```

---

### 2. Erweiterung `AgwApiService` – neue Methode `getEndpoints()`

Ruft `GET /rest/apigateway/apis/{apiId}` auf und extrahiert die
`gatewayEndPoints`-Liste:

```java
/**
 * Ruft GET /rest/apigateway/apis/{apiId} auf und gibt die Gateway-Endpoint-URLs zurück.
 * @throws IOException bei Verbindungsfehlern oder HTTP-Fehlerantworten
 */
public List<String> getEndpoints(ServerConfig server, String apiId) throws IOException
```

**Implementierungsdetails:**
- URL: `<resolveBaseUrl(server)>/rest/apigateway/apis/<apiId>`
- Header: `Authorization: Basic`, `Accept: application/json`
- JSON-Parsing: Regex/Pattern analog zu `parseApis()` – extrahiert
  `"gatewayEndPoints"\s*:\s*\[([^\]]*)\]` und splittet die enthaltenen
  String-Werte
- Gibt leere Liste zurück, wenn kein `gatewayEndPoints`-Feld vorhanden

---

### 3. Neuer Service: `EndpointCheckService`

Führt den HTTP-Check gegen einen einzelnen Endpoint durch:

```java
public class EndpointCheckService {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    /**
     * @param apiName    Name der API (wird 1:1 in das Ergebnis übernommen)
     * @param apiVersion Version der API (wird 1:1 in das Ergebnis übernommen)
     * @param url        Gateway-Endpoint-URL
     */
    public EndpointCheckResult check(String apiName, String apiVersion, String url)
}
```

**Implementierungsdetails:**
- `HttpURLConnection` / `HttpsURLConnection` – nur JDK-Boardmittel
- Trust-All SSLContext (analog zu `AgwApiService`)
- Methode: `HEAD`; bei HTTP 405 erneuter Versuch mit `GET`
- `reachable = (httpStatus >= 200 && httpStatus < 400)`
- Bei `IOException`: `EndpointCheckResult(apiName, apiVersion, url, 0, false, e.getMessage())`

---

### 4. Neuer Formatter: `EndpointCheckResultFormatter`

Formatiert `List<EndpointCheckResult>` als Tabelle. Bei einem Einzel-API-Check
wird der API-Name in der Überschrift ausgegeben; bei „Alle APIs" entfällt die
Einzelüberschrift, stattdessen erscheint eine **API**-Spalte in der Tabelle.

Signatur:

```java
public class EndpointCheckResultFormatter {
    /**
     * @param serverLabel  Hostname des Servers (für die Überschrift)
     * @param results      Liste aller Endpoint-Ergebnisse (kann mehrere APIs enthalten)
     */
    public String format(String serverLabel, List<EndpointCheckResult> results)
}
```

Die Methode erkennt automatisch, ob die Ergebnisse von einer oder mehreren
APIs stammen (`distinct apiName`-Anzahl):

- **Eine API** → Überschrift mit API-Name, keine API-Spalte in der Tabelle
- **Mehrere APIs** → generische Überschrift, zusätzliche Spalte `API` (Name + Version)

Ausgabe **eine API**:
```
Endpoint-Check für CustomerAPI v1 auf vm40757.linux.oebb.at
─────────────────────────────────────────────────────────────────────────────
  URL                                               Status  Erreichbar
  ───────────────────────────────────────────────────────────────────────────
  https://agw-host:443/gateway/CustomerAPI/v1       200     JA
  http://agw-host:5555/gateway/CustomerAPI/v1         0     NEIN  (Connection refused)
─────────────────────────────────────────────────────────────────────────────
  2 Endpoints geprüft, 1 erreichbar
```

Ausgabe **alle APIs**:
```
Endpoint-Check für alle APIs auf vm40757.linux.oebb.at
─────────────────────────────────────────────────────────────────────────────────────────
  API                       URL                                           Status  Erreichbar
  ─────────────────────────────────────────────────────────────────────────────────────────
  CustomerAPI v1            https://agw-host:443/gateway/CustomerAPI/v1   200     JA
  OrderService v2           https://agw-host:443/gateway/OrderService/v2  404     NEIN
  OrderService v2           http://agw-host:5555/gateway/OrderService/v2    0     NEIN  (timeout)
─────────────────────────────────────────────────────────────────────────────────────────
  3 Endpoints geprüft, 1 erreichbar
```

Spaltenbreiten werden dynamisch angepasst (analog zu `ApiInfoFormatter`).

---

### 5. `InteractiveMenu` – Erweiterung

Änderungen in `runActionMenu()`:

- Option `[4] Endpoint-Check` ergänzen
- Ablauf:
  1. Server auswählen (wie bei `[3]`, via `selectServer()`)
  2. API-Liste laden via `agwApiService.listApis(server)`
  3. API-Auswahlmenü anzeigen – mit Option `[a] Alle APIs` zusätzlich zu den
     nummerierten Einzeleinträgen und `[b] Zurück`
  4. Für die gewählte(n) API(s): Endpoints abrufen via
     `agwApiService.getEndpoints(server, api.getId())`
     (bei „Alle": Schleife über alle APIs)
  5. Check aller gesammelten Endpoints via `endpointCheckService.check(apiName, apiVersion, url)`
  6. Ausgabe via `endpointCheckResultFormatter.format(server.getHost(), results)`

API-Auswahlmenü (Beispiel):

```
API auswählen für vm40757.linux.oebb.at:
  [1]  CustomerAPI          v1    REST
  [2]  OrderService         v2    REST
  [3]  LegacyCalcService    10.3  SOAP
  [a]  Alle APIs
  [b]  Zurück
Auswahl:
```

Neue private Methoden:

```java
/**
 * Zeigt die API-Liste mit [a]-Option für alle.
 * Gibt eine Liste mit einer API zurück (Einzelauswahl),
 * die volle Liste (Alle) oder null bei Abbruch.
 */
private List<ApiInfo> selectApis(ServerConfig server)

/** Führt den Endpoint-Check für eine oder mehrere APIs durch und gibt das Ergebnis aus. */
private void runEndpointCheck(ServerConfig server, List<ApiInfo> apis)
```

Neue Felder in `InteractiveMenu`:

```java
private final EndpointCheckService endpointCheckService = new EndpointCheckService();
private final EndpointCheckResultFormatter endpointCheckFormatter = new EndpointCheckResultFormatter();
```

---

### 6. Tests

| Test-Klasse                             | Inhalt                                                                                      |
|-----------------------------------------|---------------------------------------------------------------------------------------------|
| `EndpointCheckResultTest.java`          | Konstruktor + Getter (inkl. `apiName`, `apiVersion`)                                        |
| `EndpointCheckResultFormatterTest.java` | Einzel-API: Überschrift mit Name, keine API-Spalte; Alle APIs: API-Spalte vorhanden; leer   |
| `AgwApiServiceTest.java`                | Erweiterung: `parseEndpoints()` / `getEndpoints()` mit Testdaten                           |
| `EndpointCheckServiceTest.java`         | Mock-HTTP-Server (lokaler ServerSocket) – prüft HEAD/GET-Fallback, 2xx/4xx                 |
| `InteractiveMenuTest.java`              | Erweiterung: `[4]` → Einzelauswahl; `[4]` → `[a]` Alle APIs                               |

---

## Tabellenausgabe – Beispiele

**Einzelne API:**
```
Endpoint-Check für CustomerAPI v1 auf vm40757.linux.oebb.at
─────────────────────────────────────────────────────────────────────────────────────
  URL                                                            Status  Erreichbar
  ─────────────────────────────────────────────────────────────────────────────────
  https://apigateway-oh-dev.oebb.at:443/gateway/CustomerAPI/v1   200     JA
  http://apigateway-oh-dev.oebb.at:5555/gateway/CustomerAPI/v1     0     NEIN  (Connection refused)
─────────────────────────────────────────────────────────────────────────────────────
  2 Endpoints geprüft, 1 erreichbar
```

**Alle APIs:**
```
Endpoint-Check für alle APIs auf vm40757.linux.oebb.at
──────────────────────────────────────────────────────────────────────────────────────────────────
  API                         URL                                                    Status  Erreichbar
  ────────────────────────────────────────────────────────────────────────────────────────────────
  CustomerAPI v1              https://apigateway-oh-dev.oebb.at:443/gateway/CustomerAPI/v1   200  JA
  OrderService v2             https://apigateway-oh-dev.oebb.at:443/gateway/OrderService/v2  404  NEIN
  LegacyCalcService v10.3     http://apigateway-oh-dev.oebb.at:5555/gateway/LegacyCalcService  0  NEIN  (timeout)
──────────────────────────────────────────────────────────────────────────────────────────────────
  3 Endpoints geprüft, 1 erreichbar
```

---

## Reihenfolge der Implementierung

1. [ ] Neues Datenmodell `EndpointCheckResult` anlegen (mit `apiName`, `apiVersion`)
2. [ ] `AgwApiService` erweitern: Methode `getEndpoints(server, apiId)`
3. [ ] Neuen Service `EndpointCheckService` implementieren (HTTP HEAD/GET)
4. [ ] `EndpointCheckResultFormatter` implementieren (Einzel- und Alle-Modus)
5. [ ] `InteractiveMenu` erweitern (Option [4], `selectApis()` mit `[a]`-Option, `runEndpointCheck()`)
6. [ ] Tests schreiben: `EndpointCheckResultFormatterTest`, `AgwApiServiceTest` (Erweiterung), `EndpointCheckServiceTest`, `InteractiveMenuTest` (Erweiterung)
7. [ ] Build + alle Tests grün (`./gradlew test`)
8. [ ] Manueller Test mit echtem Server

---

## Nicht im Scope dieses Issues

- Anzeige aller Endpoints einer API (Detailansicht, Pfade, Methoden)
- Authentifizierung beim Endpoint-Check (Consumer-Credentials)
- Paginierung bei > 750 APIs in der Auswahlliste
- Parallelisierung der Endpoint-Checks
- Farb-Ausgabe / ANSI-Codes
- Persistierung von Ergebnissen
