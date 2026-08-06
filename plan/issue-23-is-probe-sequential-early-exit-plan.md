# IS-Probe: Sequenziell mit Early-Exit – Plan

**GitHub Issue:** #23  
**Branch:** `feature/issue-23-is-probe-sequential-early-exit`

## Überblick

Aktuell ruft `IsEndpointCheckService` einen einzigen IS-Endpunkt `/check?url=…` auf, der alle Probes (Ping, TCP, HTTP) gleichzeitig ausführt. Dadurch gibt es kein Early-Exit-Verhalten und kein klares Signal, an welchem Punkt ein Endpoint ausfällt.

**Ziel:** Der Java-Client soll drei separate IS-Endpunkte (`/ping`, `/tcp`, `/http`) sequenziell aufrufen – analog zu `EndpointCheckService.check()`. Schlägt Ping fehl, werden TCP und HTTP übersprungen. Schlägt TCP fehl, wird HTTP übersprungen.

**Scope:**
- Java-Client: `IsEndpointCheckService` (Aufteilung `callCheckEndpoint` + `parseCheckResponse` in drei Methoden, sequenzielle Orchestrierung mit Early-Exit)
- Tests: `IsEndpointCheckServiceTest` (bestehende Parser-Tests anpassen, neue Early-Exit-Tests)
- IS-Paket (`OEBB_Infra_Pro_AGWCheck`) ist **außerhalb** dieses Plans – wird separat umgesetzt
- `EndpointCheckResult` bleibt unverändert
- `IsEndpointCheckConfig` / `buildBaseUrl()` bleibt unverändert

---

## Sub-Task 1: Drei HTTP-Aufruf-Methoden in `IsEndpointCheckService`

**Status:** [x] done

### Intent
Den bisherigen `callCheckEndpoint(String endpointUrl)` durch drei dedizierte Methoden ersetzen:
- `callPingEndpoint(String host)` → `GET /ping?host={host}`
- `callTcpEndpoint(String host, int port)` → `GET /tcp?host={host}&port={port}`
- `callHttpEndpoint(String url)` → `GET /http?url={url}`

Jede Methode gibt den rohen JSON-Body als `String` zurück und wirft `IOException` bei Netzwerkfehlern. Jede Methode loggt **Aufruf und Response** über `debugMsg()` / `debugBody()`, sofern `httpDebugConfig.isEnabled()` aktiv ist – identisch zum bestehenden Verhalten in `callCheckEndpoint`.

### Expected Outcomes
- Drei neue private Methoden sind in `IsEndpointCheckService` vorhanden
- `callCheckEndpoint` wird nicht mehr verwendet und kann entfernt werden
- Gemeinsame Logik (Connection aufbauen, Auth-Header, Body lesen) bleibt in `openConnection` / `readBody`
- Wenn der User im interaktiven Menü `[d]` (HTTP-Debug AN) wählt, werden für jeden der drei Aufrufe ausgegeben:
  - **Aufruf-Log:** vollständige IS-URL vor dem Request
  - **Status-Log:** HTTP-Statuscode der IS-Antwort
  - **Body-Log:** JSON-Response-Body (via `debugBody()`, nur wenn `shouldIncludeResponseBody()`)

### Todo List
1. `callPingEndpoint(String host)` implementieren:
   - URL `{baseUrl}/ping?host={host}` aufbauen
   - `debugMsg("[HTTP-DEBUG] IS GET " + fullUrl)` vor dem Request
   - `debugMsg("[HTTP-DEBUG] IS Status " + status)` nach `getResponseCode()`
   - `debugBody(body)` nach `readBody()`
   - `openConnection` + `readBody` nutzen
2. `callTcpEndpoint(String host, int port)` implementieren:
   - URL `{baseUrl}/tcp?host={host}&port={port}`
   - gleiche Debug-Aufrufe wie in Punkt 1
3. `callHttpEndpoint(String url)` implementieren:
   - URL `{baseUrl}/http?url={encodedUrl}`
   - gleiche Debug-Aufrufe wie in Punkt 1
4. `callCheckEndpoint` entfernen

### Relevant Context
- [`IsEndpointCheckService.callCheckEndpoint()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:132) – bestehende Methode als Vorlage
- [`IsEndpointCheckService.openConnection()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:162) – wiederverwendbar
- [`IsEndpointCheckService.readBody()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:186) – wiederverwendbar
- [`IsEndpointCheckConfig.buildBaseUrl()`](app/src/main/java/com/agwcontrol/IsEndpointCheckConfig.java:82) – liefert den RAD-Basispfad

---

## Sub-Task 2: Drei schlanke JSON-Parser-Methoden

**Status:** [x] done

### Intent
Den bisherigen `parseCheckResponse()` durch drei dedizierte Parser ersetzen, die jeweils nur die für ihre Probe relevanten Felder auslesen:
- `parsePingResponse(String json)` → liefert ein einfaches internes Ping-Ergebnis (`reachable`, `responseTimeMs`)
- `parseTcpResponse(String json)` → liefert ein internes TCP-Ergebnis (`open`, `responseTimeMs`)
- `parseHttpResponse(String json, String fallbackUrl)` → liefert HTTP-Felder (`status`, `reachable`, `errorMsg`)

Die Parser können als private statische Methoden implementiert werden, die ein einfaches Wertobjekt oder ein `String[]`-Array zurückgeben. Alternativ können kleine private `record`- oder Inner-Klassen genutzt werden.

> **Hinweis:** `parseCheckResponse` wird von bestehenden Unit-Tests direkt getestet. Diese Tests werden in Sub-Task 4 angepasst.

### Expected Outcomes
- Drei neue private Parse-Methoden sind vorhanden
- `parseCheckResponse` wird nicht mehr von `check()` genutzt und kann entfernt oder `private` + `@Deprecated` markiert werden (bis die Tests in Sub-Task 4 angepasst sind)

### Todo List
1. Einfache private Inner-Klassen (oder Records) `PingProbeResult`, `TcpProbeResult`, `HttpProbeResult` als Wertobjekte anlegen (innerhalb von `IsEndpointCheckService`)
2. `parsePingResponse(String json)` implementieren: liest `ping_reachable`, `ping_response_time`, gibt `PingProbeResult` zurück
3. `parseTcpResponse(String json)` implementieren: liest `tcp_open`, `tcp_response_time`, gibt `TcpProbeResult` zurück
4. `parseHttpResponse(String json, String fallbackUrl)` implementieren: liest `url`, `http_status`, `http_reachable`, `http_error_msg`, gibt `HttpProbeResult` zurück

### Relevant Context
- [`IsEndpointCheckService.parseCheckResponse()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:234) – bisherige Logik
- [`IsEndpointCheckService.JSON_STRING`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:50) – Regex-Pattern für JSON-Parsing, weiterhin nutzbar
- [`IsEndpointCheckService.parseLong()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:270) – Hilfsmethode weiterhin nutzbar
- [`IsEndpointCheckService.parseInt()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:275) – Hilfsmethode weiterhin nutzbar

---

## Sub-Task 3: Sequenzielle `check()`-Orchestrierung mit Early-Exit

**Status:** [x] done

### Intent
`check(String apiName, String apiVersion, String urlStr)` soll die drei neuen Methoden sequenziell aufrufen und bei Fehler frühzeitig abbrechen – analog zu `EndpointCheckService.check()`.

Ablauf:
1. Host + Port aus URL extrahieren (identisch zu lokalem Service)
2. Ping: `callPingEndpoint(host)` → `parsePingResponse(json)`
   - Bei `!reachable` → `EndpointCheckResult` mit Ping-FAIL, TCP/HTTP als `false`/`-1`/`0` zurückgeben
3. TCP: `callTcpEndpoint(host, port)` → `parseTcpResponse(json)`
   - Bei `!open` → `EndpointCheckResult` mit Ping-OK, TCP-FAIL, HTTP als `false`/`0` zurückgeben
4. HTTP: `callHttpEndpoint(url)` → `parseHttpResponse(json, url)`
   - Volles `EndpointCheckResult` zurückgeben

Bei `IOException` auf IS-Ebene: wie bisher `errorResult(...)` zurückgeben.

### Expected Outcomes
- `check()` ruft die IS-Endpunkte sequenziell auf
- Bei Ping-FAIL werden TCP und HTTP-Endpunkt **nicht** aufgerufen
- Bei TCP-CLOSED wird HTTP-Endpunkt **nicht** aufgerufen
- Debug-Log zeigt die drei Probes einzeln mit IS-Präfix
- Das zweite `check()`-Overload (mit `ApiDatabase`) delegiert weiterhin auf das erste

### Todo List
1. Host/Port-Extraktion aus URL in `check()` beibehalten (identisch zu aktuellem Code)
2. Ping-Block:
   - `callPingEndpoint` aufrufen (loggt Aufruf + Status + Body intern via Sub-Task 1)
   - Ergebnis parsen
   - **Ergebnis-Log:** `debugMsg("[HTTP-DEBUG] IS PING {host} → OK {ms}ms")` bzw. `"→ FAIL"`
   - Bei FAIL: Early-Return
3. TCP-Block:
   - `callTcpEndpoint` aufrufen (loggt intern)
   - Ergebnis parsen
   - **Ergebnis-Log:** `debugMsg("[HTTP-DEBUG] IS TCP  {host}:{port} → OPEN {ms}ms")` bzw. `"→ CLOSED"`
   - Bei CLOSED: Early-Return
4. HTTP-Block:
   - `callHttpEndpoint` aufrufen (loggt intern)
   - Ergebnis parsen
   - **Ergebnis-Log:** `debugMsg("[HTTP-DEBUG] IS HTTP {url} → Status {n}")` bzw. `"→ FAIL ({errorMsg})"`
5. `debugProbeConfig()` beibehalten (wird am Anfang von `check()` aufgerufen)
6. `check()` mit `ApiDatabase`-Parameter prüfen und ggf. anpassen

### Relevant Context
- [`EndpointCheckService.check()`](app/src/main/java/com/agwcontrol/EndpointCheckService.java:58) – Referenzimplementierung für sequenziellen Ablauf
- [`IsEndpointCheckService.check()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:86) – zu ändernde Methode
- [`IsEndpointCheckService.errorResult()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:280) – weiterhin nutzbar
- [`EndpointCheckResult`](app/src/main/java/com/agwcontrol/EndpointCheckResult.java) – bleibt unverändert

---

## Sub-Task 4: Tests anpassen und neue Early-Exit-Tests hinzufügen

**Status:** [x] done

### Intent
Die bestehenden Unit-Tests in `IsEndpointCheckServiceTest` testen `parseCheckResponse` direkt. Da diese Methode durch drei Parser ersetzt wird, müssen die Tests auf die neuen Parser umgestellt werden. Zusätzlich kommen Tests für das Early-Exit-Verhalten in `check()` hinzu.

### Expected Outcomes
- Alle bestehenden Tests laufen grün
- Neue Tests decken ab:
  - Ping FAIL → TCP und HTTP werden nicht aufgerufen, Ergebnis zeigt Ping-FAIL
  - TCP CLOSED → HTTP wird nicht aufgerufen, Ergebnis zeigt TCP-FAIL
  - Vollständiger Durchlauf (Ping OK, TCP OPEN, HTTP OK) liefert korrektes Ergebnis
- `parseCheckResponse`-Tests werden durch äquivalente Tests für `parsePingResponse`, `parseTcpResponse`, `parseHttpResponse` ersetzt

### Todo List
1. Bestehende Tests für `parseCheckResponse` in Tests für die drei neuen Parser übersetzen
2. Für Early-Exit-Tests: `IsEndpointCheckService` über einen Mock-fähigen Mechanismus testen (z.B. Subklasse mit überschriebenen `callPingEndpoint`/`callTcpEndpoint`/`callHttpEndpoint`-Methoden, oder `protected`-Sichtbarkeit der drei Methoden)
3. Test: Ping FAIL → TCP/HTTP nicht aufgerufen, `isPingOk()=false`, `isTcpOk()=false`, `httpStatus=0`
4. Test: TCP CLOSED → HTTP nicht aufgerufen, `isPingOk()=true`, `isTcpOk()=false`, `httpStatus=0`
5. Test: Vollständiger Durchlauf → `isPingOk()=true`, `isTcpOk()=true`, `httpStatus=200`
6. Bestehenden Test `checkReturnsErrorResultWhenIsUnreachable` prüfen – sollte mit der neuen Implementierung weiterhin funktionieren

### Relevant Context
- [`IsEndpointCheckServiceTest`](app/src/test/java/com/agwcontrol/IsEndpointCheckServiceTest.java) – vollständige Testklasse
- [`EndpointCheckServiceTest`](app/src/test/java/com/agwcontrol/EndpointCheckServiceTest.java) – Muster für sequenzielle Tests
- Sichtbarkeit der neuen `callPingEndpoint`/`callTcpEndpoint`/`callHttpEndpoint`-Methoden: `package-private` (ohne Modifier) erlaubt Tests im gleichen Package

---

## Akzeptanzkriterien (aus Issue #23)

- [ ] `IsEndpointCheckService` ruft drei IS-Endpunkte sequenziell auf
- [ ] Bei Ping FAIL: TCP und HTTP werden nicht aufgerufen
- [ ] Bei TCP CLOSED: HTTP wird nicht aufgerufen
- [ ] Log-Output zeigt Probes einzeln mit IS-Präfix (`[HTTP-DEBUG] IS PING ...` etc.)
- [ ] Alle bestehenden Tests laufen weiterhin grün
- [ ] Neue Tests decken Early-Exit-Szenarien ab

> **Hinweis:** Die IS-seitigen Endpunkte `/ping`, `/tcp`, `/http` im `OEBB_Infra_Pro_AGWCheck`-Paket sind **nicht** Teil dieses Plans und werden separat umgesetzt.
