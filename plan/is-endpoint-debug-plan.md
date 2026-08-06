# Plan: Debug-Logging für IS-Endpoint-Check

## Übersicht

**Ziel:** Wenn HTTP-Debug aktiviert ist (`[d]` im Aktionsmenü), sollen beim IS-basierten
Endpoint-Check alle relevanten Informationen ausgegeben werden, damit der Fehler
`FAIL (url parameter is required)` nachvollziehbar und diagnostizierbar ist.

**Scope:**
- `IsEndpointCheckService` bekommt dasselbe `HttpDebugConfig`/`PrintStream`-Pattern wie `AgwApiService`
- `InteractiveMenu` reicht `httpDebugConfig` und `out` an den `IsEndpointCheckService` weiter
- Keine neuen Flags, kein neues Debug-System — bestehendes `[d]`-Toggle wird genutzt

**Nicht in Scope:** Änderungen am Formatter, an Ping/TCP-Logik oder am IS-Paket selbst.

---

## Sub-Task 1 — `IsEndpointCheckService` um Debug-Ausgaben erweitern

**Intent:**
`IsEndpointCheckService` soll bei aktiviertem Debug dieselben `[HTTP-DEBUG]`-Zeilen ausgeben
wie `AgwApiService`: die vollständige URL des IS-Aufrufs, den HTTP-Status der IS-Antwort
und den rohen Response-Body. Außerdem soll die IS-Probe-Config (Scheme/Host/Port/User)
einmalig beim Aufbau des Checks ausgegeben werden, damit sofort erkennbar ist, gegen welchen
IS-Endpunkt gerufen wird.

**Expected Outcomes:**
- `IsEndpointCheckService` akzeptiert `HttpDebugConfig` und `PrintStream` im Konstruktor
- Ein Default-Konstruktor ohne Parameter bleibt für Tests erhalten (Debug deaktiviert)
- Bei aktiviertem Debug erscheinen folgende Zeilen auf `debugOut`:
  ```
  [HTTP-DEBUG] IS-Probe: https://host:port/rad/.../checkRAD  user=<user>
  [HTTP-DEBUG] GET https://host:port/rad/.../checkRAD/check?url=<encoded>
  [HTTP-DEBUG] Status 200
  [HTTP-DEBUG] Body {"url":"...","ping_reachable":"true",...}
  ```

**Todo List:**
1. Felder `httpDebugConfig` und `debugOut` zu `IsEndpointCheckService` hinzufügen
2. Konstruktor `IsEndpointCheckService(IsEndpointCheckConfig, HttpDebugConfig, PrintStream)` ergänzen
3. Bestehenden Konstruktor `IsEndpointCheckService(IsEndpointCheckConfig)` als Delegat beibehalten
4. In `callCheckEndpoint()`: vollständige URL vor dem Request ausgeben
5. In `callCheckEndpoint()`: HTTP-Status nach `conn.getResponseCode()` ausgeben
6. In `callCheckEndpoint()`: Response-Body nach `readBody()` ausgeben (nur wenn `shouldIncludeResponseBody()`)
7. Zusätzlich: IS-Probe-Config (Scheme/Host/Port/User) am Beginn von `check(...)` ausgeben

**Relevant Context:**
- [`IsEndpointCheckService.callCheckEndpoint()`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:119)
- [`AgwApiService.debugRequest/debugResponseStatus/debugResponseBody`](app/src/main/java/com/agwcontrol/AgwApiService.java:368) — exaktes Pattern übernehmen
- [`HttpDebugConfig`](app/src/main/java/com/agwcontrol/HttpDebugConfig.java)

**Status:** [ ] pending

---

## Sub-Task 2 — `InteractiveMenu` reicht Debug-Kontext an `IsEndpointCheckService` weiter

**Intent:**
Aktuell wird `IsEndpointCheckService` in `runEndpointCheck()` inline instanziiert ohne
Debug-Kontext. Der bestehende `httpDebugConfig` und `out` des Menus soll übergeben werden,
damit der Toggle `[d]` sofort wirksam ist.

**Expected Outcomes:**
- `new IsEndpointCheckService(probeConfig)` wird überall in `runEndpointCheck()` ersetzt durch
  `new IsEndpointCheckService(probeConfig, httpDebugConfig, out)`
- Zwei Stellen: einmal im Haupt-Try-Block, einmal im SQLException-Catch-Block

**Todo List:**
1. Beide `new IsEndpointCheckService(probeConfig)`-Aufrufe in `runEndpointCheck()` anpassen

**Relevant Context:**
- [`InteractiveMenu.runEndpointCheck()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:402) — Zeilen 441 und 461
- `httpDebugConfig` ist bereits ein Feld von `InteractiveMenu` (Zeile 23)
- `out` ist bereits ein Feld von `InteractiveMenu` (Zeile 17)

**Status:** [x] done

---

## Sub-Task 3 — Tests anpassen

**Intent:**
Bestehende Tests für `IsEndpointCheckService` sollen weiterhin ohne Debug-Parameter
kompilieren und laufen — der Default-Konstruktor (nur `IsEndpointCheckConfig`) bleibt
unverändert.

**Expected Outcomes:**
- Alle bestehenden Tests in `IsEndpointCheckServiceTest` laufen grün
- Kein Test muss inhaltlich geändert werden, nur ggf. neue Konstruktor-Aufrufe prüfen

**Todo List:**
1. `IsEndpointCheckServiceTest` prüfen, ob Konstruktor-Signaturen stimmen
2. `./gradlew test` ausführen — alle Tests müssen grün sein

**Relevant Context:**
- [`IsEndpointCheckServiceTest`](app/src/test/java/com/agwcontrol/IsEndpointCheckServiceTest.java)

**Status:** [ ] pending
