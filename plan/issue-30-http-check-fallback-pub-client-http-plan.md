# Issue #30 – HTTP-Check Fallback auf natives `pub.client:http` Service

**Branch:** `feat/issue-30-http-check-fallback-pub-client-http`
**Status:** 📋 Geplant / Offen

---

## Problembeschreibung

Der HTTP-Check im `IsEndpointCheckService` delegiert an den IS RAD-Endpoint (`GET /http?url=…`),
der intern den Flow-Service `checkHttp` aufruft, welcher `pub.client:http` ausführt.
Wenn der IS-HTTP-Check fehlschlägt (z. B. PKIX-Fehler weil das Backend-Zertifikat nicht im
IS-Truststore ist), liefert der IS `reachable=false` — obwohl der Endpoint tatsächlich erreichbar wäre.

Gemäß Issue #30 soll `IsEndpointCheckService` beim Fehlschlag des primären IS-HTTP-Checks einen
**Fallback-Aufruf** direkt an `pub.client:http` auf dem IS absetzen — über den nativen IS
**`/invoke/`-Endpoint**:

```
GET  /rad/…/http?url=<url>                 → primärer Aufruf (checkHttp-Flow)
  └─ reachable=false → Fallback:
POST /invoke/pub.client:http               → direkte Invokation von pub.client:http auf dem IS
  Body: url=<url>&method=get&...
```

Der `/invoke/`-Endpoint ist auf jedem IS nativ verfügbar. Es ist daher **kein neuer Flow-Service
und keine Änderung am bestehenden Package** nötig.

---

## Analyse des Ist-Zustands

### HTTP-Check in [`IsEndpointCheckService.check()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:121)

```java
// 3. HTTP
HttpProbeResult http;
try {
    String json = callHttpEndpoint(urlStr);   // → GET /rad/…/http?url=…
    http = parseHttpResponse(json, urlStr);
} catch (IOException e) {
    http = new HttpProbeResult(urlStr, 0, false, "IS probe unreachable: " + e.getMessage());
}
// ↑ Wenn IS reachable=false liefert (PKIX o. ä.): kein Fallback, Ergebnis ist fail
```

### [`IsEndpointCheckService.callHttpEndpoint()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:195)

```java
String callHttpEndpoint(String endpointUrl) throws IOException {
    String fullUrl = config.buildBaseUrl() + "/http?url=" + encodeQueryParam(endpointUrl);
    return callIsEndpoint(fullUrl);
}
```

**Problem:** Wenn der primäre `checkHttp`-Flow `reachable=false` liefert (kein IOException — IS war
erreichbar), gibt es keinen weiteren Versuch. `pub.client:http` auf IS könnte jedoch je nach
Konfiguration (z. B. IS-eigenem Truststore) erfolgreich sein, wenn direkt aufgerufen.

### Kontext: Endpoint-Check vs. HTTP-Check

| Schritt | Was | Wer macht es |
|---|---|---|
| 1. Ping | ICMP-Erreichbarkeit | `IsEndpointCheckService` → IS RAD `/ping` |
| 2. TCP  | Port offen? | `IsEndpointCheckService` → IS RAD `/tcp` |
| 3. HTTP | HTTP-Status | `IsEndpointCheckService` → IS RAD `/http` → `checkHttp`-Flow → `pub.client:http` |

---

## Entschiedene Designfragen

| Frage | Entscheidung |
|---|---|
| Wann greift der Fallback? | Wenn IS `reachable=false` liefert — bei **jedem** Fehlertyp |
| Fallback auch bei IOException (IS selbst nicht erreichbar)? | **Nein** — bei IOException ist der IS nicht erreichbar; ein Fallback bringt nichts |
| Wie wird der Fallback ausgeführt? | `POST /invoke/pub.client:http` direkt auf dem IS |
| Response-Format des Fallbacks? | IS `/invoke/` liefert JSON mit `header/status`; eigene Parse-Methode `parsePubClientHttpResponse()` |
| `error_msg` wenn auch Fallback fehlschlägt? | **Beide** Fehlermeldungen: `"primary: <IS-Fehler>; fallback: <Fallback-Fehler>"` |
| Neue Methoden nötig? | `callPubClientHttp(String url)` + `parsePubClientHttpResponse(String json, String fallbackUrl)` |

---

## Lösungsansatz

### 1. Neue Methode `callPubClientHttp()` in [`IsEndpointCheckService`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java)

Ruft `GET /invoke/pub.client:http` auf dem IS auf. Authentifizierung analog zu `callIsEndpoint()`
mit Basic Auth. Parameter werden als Query-Parameter übergeben.

Bekannte Input-Felder von `pub.client:http` (verifiziert):
`url`, `method`, `loadAs`, `args`, `table`, `string`, `bytes`, `mimeStream`, `stream`,
`encoding`, `auth`, `type`, `user`, `pass`, `delegation`, `token`, `kerberos`, `headers`,
`timeout`, `connectTimeout`, `maxKeepAliveConnections`, `keepAliveTimeout`, `newSession`,
`trustStore`, `proxyAlias`, `followRedirect`

Für den Fallback werden nur `url` und `method` benötigt:

```java
String callPubClientHttp(String endpointUrl) throws IOException {
    String fullUrl = config.buildInvokeUrl("pub.client:http")
            + "?url=" + encodeQueryParam(endpointUrl)
            + "&method=GET";
    return callIsEndpoint(fullUrl);
}
```

`buildInvokeUrl()` wird in [`IsEndpointCheckConfig`](app/src/main/java/com/agwcontrol/IsEndpointCheckConfig.java) ergänzt:

```java
public String buildInvokeUrl(String service) {
    return scheme + "://" + host + ":" + port + "/invoke/" + service;
}
```

### 2. Neue Methode `parsePubClientHttpResponse()` in [`IsEndpointCheckService`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java)

Das Response von `/invoke/pub.client:http` ist ein **flaches JSON** mit `status` als Top-Level-String-Feld
(verifiziert anhand eines echten IS-Aufrufs):

```
status        → "200"
statusMessage → "OK"
```

Das Feld `status` ist damit direkt mit dem bestehenden `parseJsonStrings()`-Regex lesbar.
Ein separater Parser ist nicht nötig — `parsePubClientHttpResponse()` delegiert an dieselbe Logik:

```java
HttpProbeResult parsePubClientHttpResponse(String json, String fallbackUrl) {
    if (json == null || json.isEmpty()) {
        return new HttpProbeResult(fallbackUrl, 0, false, "pub.client:http returned empty response");
    }
    java.util.Map<String, String> f = parseJsonStrings(json);
    int     status    = parseInt(f.getOrDefault("status", "0"), 0);
    boolean reachable = status > 0;
    String  errorMsg  = reachable ? "" : "pub.client:http: " + f.getOrDefault("statusMessage", "request failed");
    return new HttpProbeResult(fallbackUrl, status, reachable, errorMsg);
}
```

### 3. Fallback-Logik in [`IsEndpointCheckService.check()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:121)

```java
// 3. HTTP — primärer IS-Aufruf via checkHttp-Flow
HttpProbeResult http;
String primaryError = null;
try {
    String json = callHttpEndpoint(urlStr);
    http = parseHttpResponse(json, urlStr);
} catch (IOException e) {
    primaryError = "IS probe unreachable: " + e.getMessage();
    http = new HttpProbeResult(urlStr, 0, false, primaryError);
    debugMsg("[HTTP-DEBUG] IS HTTP " + urlStr + " → " + primaryError);
}

// Fallback: direkte Invokation von pub.client:http auf dem IS, wenn reachable=false
// (nur wenn IS erreichbar war, d. h. kein IOException im primären Aufruf)
if (!http.reachable && primaryError == null) {
    primaryError = http.errorMsg;
    debugMsg("[HTTP-DEBUG] IS HTTP Fallback via /invoke/pub.client:http " + urlStr
             + " — primärer Check fehlgeschlagen: " + primaryError);
    try {
        String json = callPubClientHttp(urlStr);
        HttpProbeResult fallback = parsePubClientHttpResponse(json, urlStr);
        if (fallback.reachable) {
            debugMsg("[HTTP-DEBUG] IS HTTP Fallback " + urlStr + " → Status " + fallback.status);
            http = fallback;
        } else {
            debugMsg("[HTTP-DEBUG] IS HTTP Fallback " + urlStr + " → FAIL (" + fallback.errorMsg + ")");
            http = new HttpProbeResult(urlStr, 0, false,
                    "primary: " + primaryError + "; fallback: " + fallback.errorMsg);
        }
    } catch (IOException e) {
        debugMsg("[HTTP-DEBUG] IS HTTP Fallback " + urlStr + " → FAIL (" + e.getMessage() + ")");
        http = new HttpProbeResult(urlStr, 0, false,
                "primary: " + primaryError + "; fallback: " + e.getMessage());
    }
}
```

---

## Betroffene Komponenten

| Komponente | Datei | Änderung |
|---|---|---|
| Java-Service | [`app/src/main/java/com/agwcontrol/IsEndpointCheckService.java`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java) | Fallback-Logik in `check()` + `callPubClientHttp()` + `parsePubClientHttpResponse()` |
| Java-Config | [`app/src/main/java/com/agwcontrol/IsEndpointCheckConfig.java`](app/src/main/java/com/agwcontrol/IsEndpointCheckConfig.java) | `buildInvokeUrl(String service)` ergänzen |
| Java-Tests | [`app/src/test/java/com/agwcontrol/IsEndpointCheckServiceTest.java`](app/src/test/java/com/agwcontrol/IsEndpointCheckServiceTest.java) | Fallback-Szenarien + `parsePubClientHttpResponse()` testen |

---

## Akzeptanzkriterien (aus Issue #30)

- [ ] Fallback via `/invoke/pub.client:http` wird ausgeführt, wenn IS `reachable=false` liefert (bei jedem Fehlertyp)
- [ ] Fallback wird **nicht** ausgeführt, wenn der primäre Aufruf eine `IOException` wirft (IS nicht erreichbar)
- [ ] Erfolgreicher Fallback führt zu `reachable=true` und befülltem `http_status`
- [ ] Debug-Log enthält Meldung über den Fallback-Aufruf inkl. Primärfehler
- [ ] Schlägt auch der Fallback fehl: `error_msg` enthält **beide** Fehlermeldungen (`primary: …; fallback: …`)
- [ ] `IsEndpointCheckConfig.buildInvokeUrl()` ist implementiert und getestet
- [ ] Neue/angepasste Tests in `IsEndpointCheckServiceTest` decken alle Fallback-Szenarien ab
