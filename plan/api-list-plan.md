# Plan: Issue #3 – APIs eines AGW-Servers auflisten

Branch: `feat/issue-3-api-list`

---

## Ziel

Im interaktiven Menü wird eine neue Aktion **„APIs auflisten"** ergänzt.
Für einen gewählten Server (aus der selektierten `ServerGroup`) wird per HTTP-GET
`GET /rest/apigateway/apis` aufgerufen und die zurückgelieferten APIs als Tabelle
auf der Konsole ausgegeben.

Menüeintrag im bestehenden Aktionsmenü:
```
Aktion für OH-DEV (3 Server):
  [1]  Ping
  [2]  TCP-Check
  [3]  APIs auflisten
  [b]  Zurück
  [q]  Beenden
```

Bei mehreren Servern in einer Gruppe wird zuerst ein Server-Auswahlmenü angezeigt,
da die AGW-API serverspezifisch ist.

---

## AGW REST-API (aus OpenAPI-Spec `apis.openapi.json`)

### Endpunkt

```
GET https://<host>:<port>/rest/apigateway/apis
Authorization: Basic <base64(user:password)>
Accept: application/json
```

### Query-Parameter (optional)

| Parameter | Typ     | Beschreibung                                  |
|-----------|---------|-----------------------------------------------|
| `from`    | integer | Start-Index (Paginierung)                     |
| `size`    | integer | Anzahl der zurückzugebenden APIs              |

### Response `200 OK`

```json
{
  "apiResponse": [
    {
      "api": {
        "apiName": "ChuckNorrisAPI",
        "apiVersion": "1.0",
        "apiDescription": "...",
        "isActive": false,
        "type": "REST",
        "id": "46df4227-a100-486c-9580-0bf388ec6ec7"
      },
      "responseStatus": "SUCCESS"
    }
  ]
}
```

Relevante Felder aus `APIResponseGetAPIs`:

| JSON-Feld       | Bedeutung                  |
|-----------------|----------------------------|
| `api.apiName`   | Name der API               |
| `api.apiVersion`| Version der API            |
| `api.type`      | Typ (REST, SOAP, …)        |
| `api.isActive`  | Aktiv / Inaktiv            |
| `api.id`        | UUID der API               |

### Authentifizierung

HTTP Basic Auth – Credentials kommen aus `ServerConfig.getUsername()` /
`ServerConfig.getPassword()`.

---

## Aktueller Zustand (Ist)

| Klasse / Datei                  | Relevanz                                      |
|---------------------------------|-----------------------------------------------|
| `ServerConfig.java`             | Hält Host, Port, Username, Password – **bleibt** |
| `ServerGroup.java`              | Gruppiert Server – **bleibt**                 |
| `InteractiveMenu.java`          | Aktionsmenü – **wird erweitert** (Option [3]) |
| `App.java`                      | Einstiegspunkt – **bleibt unverändert**       |
| `PingService.java`              | Ping-Logik – **bleibt unverändert**           |
| `TcpCheckService.java`          | TCP-Logik – **bleibt unverändert**            |

---

## Geplante Änderungen (Soll)

### 1. Neues Datenmodell: `ApiInfo`

Kapselt die relevanten Felder eines API-Eintrags aus der AGW-Response:

```java
public class ApiInfo {
    private final String id;
    private final String name;
    private final String version;
    private final String type;
    private final boolean active;

    // Konstruktor + Getter
}
```

---

### 2. Neuer Service: `AgwApiService`

Führt den HTTP-Aufruf durch und parst die JSON-Response.

```java
public class AgwApiService {
    /**
     * Ruft GET /rest/apigateway/apis auf und gibt die gefundenen APIs zurück.
     * @throws IOException bei Verbindungsfehlern oder HTTP-Fehlerantworten
     */
    public List<ApiInfo> listApis(ServerConfig server) throws IOException
}
```

**Implementierungsdetails:**
- Nur JDK-Boardmittel: `java.net.HttpURLConnection` (kein neues HTTP-Framework)
- URL: `https://<host>:<port>/rest/apigateway/apis`
- Header: `Authorization: Basic <base64>`, `Accept: application/json`
- TLS: `HttpsURLConnection` – da es sich um interne Server handelt, wird ein
  trust-all `SSLContext` verwendet (analog zu den bestehenden Checks, die auch
  keinen Zertifikatscheck machen)
- JSON-Parsing: Minimales eigenes Parsing via `org.json` **oder** manuelles
  String-/`javax.json`-Parsing – kein Jackson/Gson (keine neue Abhängigkeit)
- Paginierung: Zunächst wird nur die erste Seite geladen (`size=500`), um alle
  APIs in einem Aufruf zu erhalten; eine explizite Paginierungsschleife ist
  **nicht** im Scope dieses Issues.

> **Hinweis:** Das Projekt verwendet bereits `com.google.guava` – kein weiterer
> Dependency-Overhead. Für JSON-Parsing wird `javax.json` (Teil des JDK) **oder**
> ein einfaches Regex/String-basiertes Parsing genutzt.  
> Empfehlung: Abhängigkeit `org.glassfish:javax.json:1.1.4` ergänzen –
> bereits transitiv durch andere Libs verfügbar; falls nicht, in `build.gradle`
> eintragen.

---

### 3. Neuer Formatter: `ApiInfoFormatter`

Formatiert `List<ApiInfo>` als ausgerichtete Tabelle:

```
ChuckNorrisAPI        1.0    REST    ACTIVE
PetstoreAPI           2.1    REST    INACTIVE
CalcService           10.3   SOAP    ACTIVE
```

```java
public class ApiInfoFormatter {
    public String format(List<ApiInfo> apis)
}
```

Spaltenbreiten werden dynamisch an den längsten Wert angepasst
(analog zu `PingResultFormatter`).

---

### 4. `InteractiveMenu` – Erweiterung

Änderungen in `runActionMenu()`:

- Option `[3] APIs auflisten` ergänzen
- Bei **einem Server** in der Gruppe → direkt `runApiList(server)` aufrufen
- Bei **mehreren Servern** → Server-Auswahlmenü anzeigen (Index 1…n), dann
  `runApiList(selectedServer)` aufrufen

Neue private Methode:

```java
private void runApiList(ServerConfig server) {
    try {
        List<ApiInfo> apis = agwApiService.listApis(server);
        out.println();
        out.println(apiInfoFormatter.format(apis));
    } catch (IOException e) {
        out.println("Fehler beim Abrufen der APIs: " + e.getMessage());
    }
}
```

---

### 5. `build.gradle` – ggf. JSON-Abhängigkeit

Falls `javax.json` nicht transitiv vorhanden:

```groovy
implementation 'org.glassfish:javax.json:1.1.4'
```

---

### 6. Tests

| Test-Klasse               | Inhalt                                                                    |
|---------------------------|---------------------------------------------------------------------------|
| `ApiInfoTest.java`        | Konstruktor + Getter                                                      |
| `ApiInfoFormatterTest.java` | Formatierungsausgabe mit 1, n und 0 APIs                                |
| `AgwApiServiceTest.java`  | Mock-HTTP-Server (MockWebServer oder lokaler ServerSocket) – listet APIs; prüft Auth-Header, prüft Fehlerbehandlung bei 401/500 |
| `InteractiveMenuTest.java`| Erweiterung: Option `[3]` im Aktionsmenü öffnet Server-Auswahl / direkt API-Liste |

---

## Tabellenausgabe – Beispiel

```
AGW-Control – APIs auf vm40757.linux.oebb.at:443
──────────────────────────────────────────────────────────────
  Name                     Version    Typ     Status
  ──────────────────────────────────────────────────────────
  CustomerAPI              v1         REST    AKTIV
  OrderService             2.0        REST    INAKTIV
  LegacyCalcService        10.3       SOAP    AKTIV
──────────────────────────────────────────────────────────────
  3 APIs gefunden
```

---

## Reihenfolge der Implementierung

1. [ ] Neue Klasse `ApiInfo` anlegen
2. [ ] Neuen Service `AgwApiService` implementieren (HTTP-Call + JSON-Parsing)
3. [ ] `ApiInfoFormatter` implementieren
4. [ ] `InteractiveMenu` erweitern (Option [3] + Server-Auswahl)
5. [ ] `build.gradle` prüfen / JSON-Lib ergänzen
6. [ ] Tests schreiben: `ApiInfoFormatterTest`, `AgwApiServiceTest`, `InteractiveMenuTest` erweitern
7. [ ] Build + alle Tests grün (`./gradlew test`)
8. [ ] Manueller Test mit echtem Server

---

## Nicht im Scope dieses Issues

- Endpoints einer API auflisten (→ Issue #4)
- Endpoint-Check (→ Issue #5)
- Paginierung bei > 500 APIs
- Farb-Ausgabe / ANSI-Codes
- Caching der API-Liste
