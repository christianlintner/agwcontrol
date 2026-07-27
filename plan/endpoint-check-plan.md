# Plan: Issue #5 – Endpoint-Check für eine AGW-API

Branch: `feat/issue-5-endpoint-check`

---

## Ziel

Im interaktiven Menü wird eine neue Aktion **„Endpoint-Check"** ergänzt.
Für eine gewählte API (aus der API-Liste eines Servers) werden deren Gateway-Endpoints
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
dann die API-Liste geladen und eine API ausgewählt,
anschließend werden die Gateway-Endpoints dieser API abgefragt und geprüft.

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

Kapselt das Ergebnis eines HTTP-Checks gegen einen Gateway-Endpoint:

```java
public class EndpointCheckResult {
    private final String url;
    private final int httpStatus;
    private final boolean reachable;
    private final String errorMsg;

    public EndpointCheckResult(String url, int httpStatus, boolean reachable, String errorMsg) { ... }

    // Getter
    public String getUrl()       { ... }
    public int getHttpStatus()   { ... }
    public boolean isReachable() { ... }
    public String getErrorMsg()  { ... }
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

    public EndpointCheckResult check(String url)
}
```

**Implementierungsdetails:**
- `HttpURLConnection` / `HttpsURLConnection` – nur JDK-Boardmittel
- Trust-All SSLContext (analog zu `AgwApiService`)
- Methode: `HEAD`; bei HTTP 405 erneuter Versuch mit `GET`
- `reachable = (httpStatus >= 200 && httpStatus < 400)`
- Bei `IOException`: `EndpointCheckResult(url, 0, false, e.getMessage())`

---

### 4. Neuer Formatter: `EndpointCheckResultFormatter`

Formatiert `List<EndpointCheckResult>` als Tabelle:

```
Endpoint-Check für CustomerAPI v1 auf vm40757.linux.oebb.at
─────────────────────────────────────────────────────────────────────────────
  URL                                             Status  Erreichbar
  ─────────────────────────────────────────────────────────────────────────
  https://agw-host:443/gateway/CustomerAPI/v1     200     JA
  http://agw-host:5555/gateway/CustomerAPI/v1     0       NEIN  (Connection refused)
─────────────────────────────────────────────────────────────────────────────
  2 Endpoints geprüft, 1 erreichbar
```

```java
public class EndpointCheckResultFormatter {
    public String format(String apiLabel, List<EndpointCheckResult> results)
}
```

Spaltenbreiten werden dynamisch angepasst (analog zu `ApiInfoFormatter`).

---

### 5. `InteractiveMenu` – Erweiterung

Änderungen in `runActionMenu()`:

- Option `[4] Endpoint-Check` ergänzen
- Ablauf:
  1. Server auswählen (wie bei `[3]`, via `selectServer()`)
  2. API-Liste laden via `agwApiService.listApis(server)`
  3. API aus der Liste auswählen (`selectApi()`)
  4. Endpoints abrufen via `agwApiService.getEndpoints(server, api.getId())`
  5. Check aller Endpoints via `endpointCheckService.check(url)`
  6. Ausgabe via `endpointCheckResultFormatter.format(...)`

Neue private Methoden:

```java
/** Zeigt die API-Liste und lässt den Nutzer eine auswählen. Gibt null zurück bei Abbruch. */
private ApiInfo selectApi(ServerConfig server)

/** Führt den Endpoint-Check für eine API durch und gibt das Ergebnis aus. */
private void runEndpointCheck(ServerConfig server, ApiInfo api)
```

Neue Felder in `InteractiveMenu`:

```java
private final EndpointCheckService endpointCheckService = new EndpointCheckService();
private final EndpointCheckResultFormatter endpointCheckFormatter = new EndpointCheckResultFormatter();
```

---

### 6. Tests

| Test-Klasse                          | Inhalt                                                                       |
|--------------------------------------|------------------------------------------------------------------------------|
| `EndpointCheckResultTest.java`       | Konstruktor + Getter                                                         |
| `EndpointCheckResultFormatterTest.java` | Formatierungsausgabe: 1 erreichbar, 1 nicht erreichbar, leer              |
| `AgwApiServiceTest.java`             | Erweiterung: `parseEndpoints()` / `getEndpoints()` mit Testdaten            |
| `EndpointCheckServiceTest.java`      | Mock-HTTP-Server (lokaler ServerSocket) – prüft HEAD/GET-Fallback, 2xx/4xx  |
| `InteractiveMenuTest.java`           | Erweiterung: Option `[4]` navigiert durch Server → API → Endpoints          |

---

## Tabellenausgabe – Beispiel

```
Endpoint-Check für CustomerAPI v1 auf vm40757.linux.oebb.at
─────────────────────────────────────────────────────────────────────────────────────
  URL                                                          Status  Erreichbar
  ─────────────────────────────────────────────────────────────────────────────────
  https://apigateway-oh-dev.oebb.at:443/gateway/CustomerAPI/v1   200     JA
  http://apigateway-oh-dev.oebb.at:5555/gateway/CustomerAPI/v1     0     NEIN  (Connection refused)
─────────────────────────────────────────────────────────────────────────────────────
  2 Endpoints geprüft, 1 erreichbar
```

---

## Reihenfolge der Implementierung

1. [ ] Neues Datenmodell `EndpointCheckResult` anlegen
2. [ ] `AgwApiService` erweitern: Methode `getEndpoints(server, apiId)`
3. [ ] Neuen Service `EndpointCheckService` implementieren (HTTP HEAD/GET)
4. [ ] `EndpointCheckResultFormatter` implementieren
5. [ ] `InteractiveMenu` erweitern (Option [4], `selectApi()`, `runEndpointCheck()`)
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
