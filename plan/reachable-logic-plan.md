# Plan: Änderung reachable-Logik in agwcontrol

## Übersicht

**Ziel:** Die `reachable`-Statusberechnung im Tool `agwcontrol` lockern. Statt dass ausschließlich ein HTTP-Statuscode `> 0` die Erreichbarkeit bestätigt, reicht es künftig aus, wenn **entweder TCP offen ist oder ein HTTP-Statuscode zurückgekommen ist**.

**Scope:** Nur `/Users/christianlintner/github/agwcontrol/`. Kein Eingriff am Konverter-Skript oder den Markdown-Reports im aufrufenden Repository.

**Auslöser:** `VstiDatendrehscheibe_API 1.7 (DEV)` — `tcp_ok=true`, `http_status=0` (HTTP nicht erreichbar/geblockt) → `reachable=false` obwohl der Port offen ist.

---

## Aktuelle Logik (Code-Befund)

**Entgegen der ursprünglichen Vermutung ist `ping_ok` aktuell irrelevant für `reachable`.**

In [`EndpointCheckService.java:97`](app/src/main/java/com/agwcontrol/EndpointCheckService.java:97):
```java
boolean reachable = httpStatus > 0;
```

`reachable` hängt ausschließlich am HTTP-Check. TCP und Ping werden gemessen, aber nicht zur Berechnung herangezogen.

In [`IsEndpointCheckService.java:134-136`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java:134-136) wird `http.reachable` direkt aus dem IS-Probe-JSON gelesen (ebenfalls HTTP-basiert).

**Konsequenz des Fehlerfalls:**
- `tcp_ok=true`, `http_status=0` → `reachable = (0 > 0)` → `false` ❌ (obwohl Port offen)

---

## Neue Logik

```java
boolean reachable = tcp.isOpen() || httpStatus > 0;
```

Eine API gilt als erreichbar, wenn:
- der TCP-Port offen ist (`tcp.isOpen()`) **ODER**
- ein HTTP-Statuscode zurückgekommen ist (`httpStatus > 0`, auch 401, 404, 503)

`ping_ok` fließt weiterhin **nicht** in `reachable` ein und wird nur für Diagnosezwecke ins CSV geschrieben.

---

## Konkrete Auswirkung

| API | tcp_ok | http_status | Bisherig | Neu |
|-----|--------|-------------|----------|-----|
| VstiDatendrehscheibe_API 1.7 (DEV, DN2020) | true | 0 | ❌ false | ✅ true |
| YM-ServiceIN_API 1.0 (DN2020-PreProd) | true | 503 | ✅ true | ✅ true (unverändert) |
| Endpoint mit tcp=false, http_status=0 | false | 0 | ❌ false | ❌ false (korrekt) |

---

## Sub-Tasks

### Sub-Task 1: Neue reachable-Logik in EndpointCheckService implementieren

**Intent:** Die `reachable`-Berechnung in `EndpointCheckService` auf `tcp_ok OR http_ok` umstellen.

**Expected Outcomes:**
- `reachable=true` wenn `tcp_ok=true` ODER `http_status > 0`
- `reachable=false` nur wenn weder TCP offen noch HTTP-Status vorhanden
- `ping_ok` wird weiterhin gemessen und ins CSV geschrieben, beeinflusst `reachable` nicht

**Todo List:**
1. In [`EndpointCheckService.java:97`](app/src/main/java/com/agwcontrol/EndpointCheckService.java:97) die Zeile ändern:
   - Von: `boolean reachable = httpStatus > 0;`
   - Zu: `boolean reachable = tcp.isOpen() || httpStatus > 0;`

**Relevant Context:**
- Betroffene Datei: `app/src/main/java/com/agwcontrol/EndpointCheckService.java`, Zeile 97
- `tcp` ist bereits als lokale Variable verfügbar (Zeile 81: `TcpCheckResult tcp = tcpService.check(...)`)
- **Nachtrag:** `IsEndpointCheckService` war ebenfalls betroffen — dort wurde `http.reachable` direkt übergeben ohne TCP einzubeziehen. Fix in Zeile 134–136.

**Status:** [x] done

---

### Sub-Task 2: Unit-Tests für neue reachable-Logik ergänzen

**Intent:** Sicherstellen, dass die neue Logik korrekt getestet ist und keine Regression eingeführt wird.

**Expected Outcomes:**
- Neuer Test: `reachableWhenTcpOpenButNoHttp` — `tcp_ok=true`, `http_status=0` → `reachable=true`
- Bestehende Tests bleiben grün (HTTP 200/404/500 → `reachable=true` unverändert)
- Bestehender Test `unreachableHostReturnsStatus0` bleibt grün (tcp=false, http=0 → `reachable=false`)

**Todo List:**
1. In [`EndpointCheckServiceTest.java`](app/src/test/java/com/agwcontrol/EndpointCheckServiceTest.java) einen neuen Test hinzufügen:
   - Einen TCP-Server starten, der die Verbindung annimmt aber keine valide HTTP-Antwort liefert (kein `HTTP/` im Response)
   - Prüfen: `r.isTcpOk()` ist `true`, `r.getHttpStatus()` ist `0`, `r.isReachable()` ist `true`
2. Alle bestehenden Tests mit `./gradlew test` ausführen und sicherstellen, dass alle grün sind

**Relevant Context:**
- Testdatei: `app/src/test/java/com/agwcontrol/EndpointCheckServiceTest.java`
- Hilfsmethode `startOneShotServer(response)` kann wiederverwendet werden — einen leeren Response-String übergeben, damit kein valider HTTP-Status zurückkommt
- `DbReportServiceTest.java` Zeile 161: `assertEquals("true", cols[13])` — prüft `reachable`-Spalte im CSV, muss weiterhin grün bleiben

**Status:** [x] done

