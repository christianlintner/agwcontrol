# Issue #22 – checkRAD: Umstellung auf IS-URL und /invoke statt /rest

## Übersicht

Alle Endpoint-Checks werden derzeit über den REST-Descriptor-Pfad
`/rest/OEBB_Infra_Pro_AGWControl/at.oebb.infra.pro.agwctl.pub.rs.v1:checkRAD`
aufgerufen. Ziel ist die vollständige Umstellung auf den nativen IS-Invoke-Pfad
`/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services` für alle fünf
Service-Operationen.

Gleichzeitig wird die Basis-URL des Probe-Endpunkts nicht mehr aus der
Haupt-URL des KeePass-Eintrags gebildet, sondern ausschließlich aus dem Custom
Field `IS-URL`. Username und Password kommen weiterhin aus den
Standard-KeePass-Feldern.

---

## Sub-Task 1 – `IsEndpointCheckConfig`: Invoke-Pfad und IS-URL-Konstruktor

**Status:** [x] done

### Intent
`RAD_BASE_PATH` enthält den alten REST-Pfad. Dieser muss auf den
Invoke-Pfad umgestellt werden. Außerdem braucht die Klasse einen neuen
Konstruktor, der die vollständige `IS-URL` (z. B.
`https://vm40757.linux.oebb.at:5559`) als fertige URL-Zeichenkette
entgegennimmt, weil `KeePassConfigLoader` in Sub-Task 2 genau das liefert.

### Expected Outcomes
- `RAD_BASE_PATH` hat den Wert
  `/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services`
- `buildBaseUrl()` liefert z. B.
  `https://vm40757.linux.oebb.at:5559/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services`
- Neuer Konstruktor `IsEndpointCheckConfig(String isUrl, String username, String password)`
  parst `isUrl` und befüllt scheme/host/port intern
- `toString()` gibt weiterhin kein Passwort aus

### Todo-Liste
- [ ] `RAD_BASE_PATH` auf
  `/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services` ändern
- [ ] Javadoc-Kommentare in `IsEndpointCheckConfig` auf den neuen Pfad
  aktualisieren
- [ ] Neuen Konstruktor `IsEndpointCheckConfig(String isUrl, String username,
  String password)` hinzufügen, der `isUrl` per `URI` in scheme/host/port
  zerlegt (Fehlerbehandlung bei ungültiger URL via `IllegalArgumentException`)

### Relevant Context
- [`IsEndpointCheckConfig.java`](app/src/main/java/com/agwcontrol/IsEndpointCheckConfig.java)
- Aktueller Wert: `RAD_BASE_PATH = "/rest/OEBB_Infra_Pro_AGWControl/at.oebb.infra.pro.agwctl.pub.rs.v1:checkRAD"`
- Zielwert: `RAD_BASE_PATH = "/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services"`

---

## Sub-Task 2 – `KeePassConfigLoader`: IS-URL als Quelle für `IsEndpointCheckConfig`

**Status:** [x] done

### Intent
Aktuell baut `KeePassConfigLoader.entriesFromGroup()` die
`IsEndpointCheckConfig` aus dem Haupt-URL-Feld des KeePass-Eintrags
(scheme, host, port). Dies ist falsch: der IS-Invoke-Endpunkt hat eine eigene
URL (Custom Field `IS-URL`), die von der AGW-URL abweichen kann (anderer Host
und/oder Port). Die Konstruktion der `IsEndpointCheckConfig` muss auf das
Custom Field `IS-URL` umgestellt werden.

Ist das Custom Field `IS-URL` nicht gesetzt, soll `isProbeConfig` auf `null`
belassen werden (kein Fallback auf die Haupt-URL).

### Expected Outcomes
- `entriesFromGroup()` liest `IS-URL` aus dem Custom Field und baut daraus
  `IsEndpointCheckConfig(isUrl, username, password)` via dem in Sub-Task 1
  ergänzten Konstruktor
- Fehlt `IS-URL` (null oder leer), wird kein `isProbeConfig` gesetzt
- Username und Password kommen weiterhin aus den Standard-KeePass-Feldern

### Todo-Liste
- [ ] `entriesFromGroup()` in `KeePassConfigLoader` anpassen: `isProbeConfig`
  nur setzen, wenn `isUrl != null`; Konstruktion aus dem neuen Konstruktor
  `IsEndpointCheckConfig(isUrl, username, password)`
- [ ] Veralteten Kommentar in `entriesFromGroup()` aktualisieren

### Relevant Context
- [`KeePassConfigLoader.java`](app/src/main/java/com/agwcontrol/KeePassConfigLoader.java)
  – `entriesFromGroup()`, Zeilen 61–91
- [`ServerConfig.java`](app/src/main/java/com/agwcontrol/ServerConfig.java)
  – `setIsProbeConfig()` bleibt unverändert

---

## Sub-Task 3 – `IsEndpointCheckService`: Alle 5 Invoke-Operationen implementieren

**Status:** [x] done

### Intent
`IsEndpointCheckService` kennt aktuell nur den kombinierten `checkAll`-Aufruf
(intern `/check`). Nach der Umstellung auf den Invoke-Pfad sollen auch die
vier einzelnen Operationen als öffentliche Methoden verfügbar sein:
`resolveHost`, `checkPing`, `checkTcp`, `checkHttp`. Außerdem muss das
bestehende Pfadsegment `/check` auf `/checkAll` korrigiert werden.

### Expected Outcomes
- `callCheckEndpoint()` ruft `buildBaseUrl() + "/checkAll?url=..."` auf
- Neue Methoden: `resolveHost(url)`, `checkPing(host)`, `checkTcp(host, port)`,
  `checkHttp(url)` – jede macht einen eigenen Invoke-GET und gibt ein
  typisiertes Ergebnisobjekt zurück (oder ein einfaches POJO/String-Map falls
  noch kein dediziertes Result-Objekt existiert)
- Javadoc in `IsEndpointCheckService` spiegelt alle fünf Invoke-Pfade wider

### Todo-Liste
- [ ] `callCheckEndpoint()`: Pfadsegment `/check` durch `/checkAll` ersetzen
- [ ] Generische private Hilfsmethode `callInvoke(String operation, String queryString)`
  ergänzen, die `buildBaseUrl() + "/" + operation + "?" + queryString` aufruft
- [ ] `resolveHost(String url)` implementieren: ruft `/resolveHost?url=...` auf,
  parst JSON-Felder `host` und `resolved_ip`, gibt ein Ergebnisobjekt zurück
- [ ] `checkPing(String host)` implementieren: ruft `/checkPing?host=...` auf,
  parst `host`, `reachable`, `response_time`, gibt `PingResult` zurück
- [ ] `checkTcp(String host, int port)` implementieren: ruft
  `/checkTcp?host=...&port=...` auf, parst `host`, `port`, `open`,
  `response_time`, gibt `TcpCheckResult` zurück
- [ ] `checkHttp(String url)` implementieren: ruft `/checkHttp?url=...` auf,
  parst `url`, `http_status`, `reachable`, `error_msg`, gibt
  `EndpointCheckResult` zurück (nur HTTP-Teil, Ping/TCP auf -1/false)
- [ ] Javadoc der Klasse und aller neuen Methoden aktualisieren

### Relevant Context
- [`IsEndpointCheckService.java`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java)
  – `callCheckEndpoint()`, Zeile 121; bestehende JSON-Parsing-Hilfsmethoden
- [`PingResult.java`](app/src/main/java/com/agwcontrol/PingResult.java) – vorhandenes Result-Objekt für Ping
- [`TcpCheckResult.java`](app/src/main/java/com/agwcontrol/TcpCheckResult.java) – vorhandenes Result-Objekt für TCP
- [`EndpointCheckResult.java`](app/src/main/java/com/agwcontrol/EndpointCheckResult.java) – vorhandenes Result-Objekt für HTTP/kombiniert
- OpenAPI-Spec [`services/checkRAD/openapi.yaml`](services/checkRAD/openapi.yaml) – definiert exakte Parameter-Namen pro Operation

---

## Sub-Task 4 – `services/checkRAD/openapi.yaml`: Server-URL auf `/invoke` umstellen

**Status:** [x] done

### Intent
Die OpenAPI-Spezifikation beschreibt den REST-Descriptor-Pfad als `servers[].url`.
Dieser soll auf den Invoke-Pfad angepasst werden. Die Server-Variablen
(scheme, host, port) werden entfernt, da IS-URL und Credentials nicht über
OpenAPI-Variablen konfiguriert werden, sondern aus KeePass kommen.

### Expected Outcomes
- `servers[].url` enthält nur noch den festen Invoke-Basispfad (ohne Variablen)
- `servers[].variables`-Block entfällt

### Todo-Liste
- [ ] `servers[].url` auf
  `/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services` setzen
- [ ] `variables`-Block aus `servers[]` entfernen

### Relevant Context
- [`services/checkRAD/openapi.yaml`](services/checkRAD/openapi.yaml), Zeilen 15–27

---

## Sub-Task 5 – Tests anpassen

**Status:** [x] done

### Intent
Die geänderten Konstanten und das neue Verhalten müssen in den bestehenden
Tests reflektiert werden. Insbesondere prüfen `IsEndpointCheckConfigTest` und
`KeePassConfigLoaderTest` explizit alte Werte oder altes Verhalten.

### Expected Outcomes
- Alle bestehenden Tests bauen grün durch (`./gradlew test`)
- `IsEndpointCheckConfigTest.radBasePathIsCorrect()` prüft den neuen Invoke-Pfad
- `IsEndpointCheckConfigTest.buildBaseUrlHttp/Https()` prüfen die neuen URLs
- `KeePassConfigLoaderTest.isProbeConfigBuiltFromStandardFields()` prüft, dass
  `isProbeConfig` aus `IS-URL` gebaut wird (host/port aus der IS-URL, nicht
  der AGW-URL) — Test-kdbx-Daten ggf. via `UpdateTestKdbx` anpassen

### Todo-Liste
- [ ] `IsEndpointCheckConfigTest`: `radBasePathIsCorrect()` auf den neuen Pfad
  anpassen; `buildBaseUrlHttp/Https()` auf neuen Pfad anpassen
- [ ] `KeePassConfigLoaderTest.isProbeConfigBuiltFromStandardFields()`:
  erwartete Werte auf IS-URL-Basis anpassen (host/port kommen nun aus `IS-URL`,
  nicht aus dem Haupt-URL-Feld)
- [ ] `IsEndpointCheckServiceTest`: kein Handlungsbedarf erwartet (testet nur
  JSON-Parsing, das unverändert bleibt)
- [ ] `./gradlew test` ausführen und alle Fehler beheben

### Relevant Context
- [`IsEndpointCheckConfigTest.java`](app/src/test/java/com/agwcontrol/IsEndpointCheckConfigTest.java)
- [`KeePassConfigLoaderTest.java`](app/src/test/java/com/agwcontrol/KeePassConfigLoaderTest.java)
- [`UpdateTestKdbx.java`](app/src/test/java/com/agwcontrol/UpdateTestKdbx.java)
  – prüfen, ob IS-URL der test.kdbx-Einträge korrekt gesetzt ist
