# Plan: Issue #4 – Endpoints einer API prüfen (Ping + TCP-Check)

Branch: `feat/issue-4-endpoint-check`

---

## Ziel

Erweiterung des bestehenden Endpoint-Checks um **drei kombinierte Checks**
pro aufgelöstem nativen Endpoint:

| Spalte         | Check-Art   | Ziel                        | Service                |
|----------------|-------------|-----------------------------|------------------------|
| **Ping**       | ICMP-Ping   | Host (aus URL extrahiert)   | `PingService`          |
| **TCP**        | TCP-Connect | Host + Port (aus URL)       | `TcpCheckService`      |
| **HTTP Status** | HTTP(S)    | vollständige URL (HEAD/GET) | `EndpointCheckService` |

Der Benutzer wählt im interaktiven Menü Aktion `[4] Endpoint-Check`, wählt einen
Server und eine oder alle APIs. Für jeden aufgelösten nativen Endpoint werden alle
drei Checks nacheinander ausgeführt.

> **Hinweis zur CLI-Anforderung:** Issue #4 beschreibt ursprünglich einen
> `check --server <host> --api <api-name>` CLI-Befehl. Da `agwcontrol` ausschließlich
> als interaktive Anwendung gebaut ist (KeePass-Credentials zwingend erforderlich,
> kein CLI-only-Modus) und Issues #1–#3 ebenfalls interaktiv umgesetzt wurden,
> wird die Funktionalität im interaktiven Menü unter Aktion `[4]` realisiert.

---

## Aktueller Zustand (Ist)

| Klasse / Datei                      | Status                                                                      |
|-------------------------------------|-----------------------------------------------------------------------------|
| `RoutingEndpoint.java`              | ✅ vorhanden – direkter + Alias-Endpoint                                    |
| `AgwApiService.java`                | ✅ vorhanden – `getNativeEndpoints()`, `resolveAlias()`, `listApis()`       |
| `EndpointCheckService.java`         | ✅ vorhanden – HTTP HEAD/GET Check                                          |
| `EndpointCheckResult.java`          | ✅ vorhanden – enthält `aliasName`, `url`, `httpStatus`, `reachable`        |
| `EndpointCheckResultFormatter.java` | ✅ vorhanden – Alias-Spalte, Single/Multi-API-Tabelle                       |
| `InteractiveMenu.java`              | ✅ vorhanden – Option `[4]`, `selectApis()`, `runEndpointCheck()`           |
| `PingService.java`                  | ✅ vorhanden – ICMP-Ping                                                    |
| `TcpCheckService.java`              | ✅ vorhanden – TCP-Connect-Check                                            |
| `PingResult.java`                   | ✅ vorhanden                                                                |
| `TcpCheckResult.java`               | ✅ vorhanden                                                                |

**Was bereits implementiert ist (aus Issue #5-Plan):**
- Alias-Auflösung über `GET /alias/{aliasId}`
- HTTP-Check gegen Backend-URL (HEAD/GET)
- Tabellen-Ausgabe mit Alias-Spalte
- Interaktives Menü Option [4] mit API-Auswahl

**Was fehlt (für Issue #4):**
- `EndpointCheckResult` kennt nur HTTP-Status; kein Ping- und TCP-Ergebnis
- Der Endpoint-Check führt aktuell nur einen HTTP-Check durch – Ping und TCP fehlen
- Ausgabetabelle zeigt keinen Ping-Status, TCP-Status und keine Antwortzeiten
- Keine Zusammenfassung „N/M OK"

---

## Analyse: Was muss umgesetzt werden?

### 1. `EndpointCheckResult` erweitern

Aktuell: HTTP-Status-basiertes Ergebnis.

Fehlend: Ping-Status + Latenz, TCP-Status + Latenz.

```java
public class EndpointCheckResult {
    // bereits vorhanden:
    private final String apiName;
    private final String apiVersion;
    private final String aliasName;     // nullable
    private final String url;
    private final int httpStatus;
    private final boolean reachable;
    private final String errorMsg;

    // NEU:
    private final boolean pingOk;
    private final long pingMs;          // -1 wenn nicht verfügbar
    private final boolean tcpOk;
    private final long tcpMs;           // -1 wenn nicht verfügbar
}
```

**Rückwärtskompatibilität:** Bestehende Konstruktoren bleiben erhalten; neuer
Konstruktor mit allen Feldern wird ergänzt. Alternativ: Ping/TCP als separates
Ergebnis-Feld (record/DTO).

---

### 2. `EndpointCheckService` erweitern

Aktuell: Nur HTTP HEAD/GET.

Gefordert: Für jeden Endpoint wird **Ping + TCP** durchgeführt.

**Option A – `EndpointCheckService` integriert Ping + TCP:**
- `check()` bekommt `ServerConfig` oder Host+Port als Parameter
- Intern werden `PingService.ping()` und `TcpCheckService.check()` aufgerufen
- Rückgabe: `EndpointCheckResult` mit allen drei Werten (HTTP bleibt optional)

**Option B – Getrennte Services, Zusammenführung in `InteractiveMenu`:**
- `runEndpointCheck()` ruft nacheinander `pingService`, `tcpService` auf
- Ergebnis wird zu einem `EndpointCheckResult` zusammengeführt

> **Empfehlung: Option A** – kapselt die Logik im Service, Menu bleibt schlank.

**Neue Signatur:**

```java
/**
 * Prüft einen Backend-Endpoint per Ping, TCP-Connect und optionalem HTTP-Check.
 *
 * @param apiName     API-Name
 * @param apiVersion  API-Version
 * @param resolvedUrl Aufgelöste Backend-URL (z.B. https://backend:8080/service)
 */
public EndpointCheckResult check(String apiName, String apiVersion, String resolvedUrl)
```

Intern:
1. Host aus `resolvedUrl` extrahieren (via `new URL(resolvedUrl).getHost()`)
2. Port aus `resolvedUrl` extrahieren (`getPort()`, Default 443 für https / 80 für http)
3. `PingService.ping(host, timeout)` → `PingResult`
4. `TcpCheckService.check(host, port, timeout)` → `TcpCheckResult`
5. HTTP HEAD/GET (wie bisher) → HTTP-Status (bleibt als Zusatzinfo erhalten)
6. `EndpointCheckResult` mit allen Werten zurückgeben

> **Signaturerweiterung nötig:**
> `PingService` und `TcpCheckService` akzeptieren aktuell nur `ServerConfig`-Objekte.
> Neue Überladungen werden ergänzt:
> - `PingService.ping(String host, int timeoutMs)` → `PingResult`
> - `TcpCheckService.check(String host, int port, int timeoutMs)` → `TcpCheckResult`
> Die bestehenden `ping(ServerConfig)` und `check(ServerConfig)` Methoden bleiben unverändert.

---

### 3. `EndpointCheckResultFormatter` anpassen

**Semantik der drei Checks (geklärte Anforderung):**
- `Ping`       → ICMP gegen den **Host** (aus URL extrahiert, z.B. `myDevstage`)
- `TCP`        → TCP-Connect gegen **Host:Port** (aus URL extrahiert, z.B. `myDevstage:9090`)
- `HTTP Status` → HTTP(S) HEAD/GET gegen die **vollständige URL**

Aktuelle Tabelle (nur HTTP):
```
  Alias / Endpoint    URL                      HTTP Status
  MystageEndpoint     https://myDevstage:9090  OK (200)
```

Neue Tabelle mit allen drei Checks:
```
  Alias / Endpoint         URL                       Ping        TCP          HTTP Status
  ────────────────────────────────────────────────────────────────────────────────────────
  MystageEndpoint (Alias)  https://myDevstage:9090   OK  12ms    OPEN  8ms    OK (200)
  https://backend:8080     (direkt)                  FAIL  -     CLOSED  -    FAIL (timeout)
  ────────────────────────────────────────────────────────────────────────────────────────
  Ergebnis: 1/2 OK
```

**Spalten:**
| Spalte           | Inhalt                                                                          |
|------------------|---------------------------------------------------------------------------------|
| Alias / Endpoint | Alias-Name + `(Alias)` oder direkte URL                                         |
| URL              | aufgelöste Backend-URL (oder `(direkt)`)                                        |
| Ping             | `OK Xms` / `FAIL  -`                                                            |
| TCP              | `OPEN Xms` / `CLOSED  -`                                                        |
| HTTP Status      | `OK (200)` / `OK (404)` / `OK (500)` / `FAIL (Fehlermeldung)` / `FAIL`         |

**Zusammenfassung:**
```
  Ergebnis: N/M OK
```
Ein Endpoint gilt als **OK** wenn: `pingOk && tcpOk && reachable`

> **HTTP-Status-Definition:**
> Jede HTTP-Antwort (inkl. 4xx und 5xx) = `OK (Status)` – der Server antwortet.
> Nur Timeout oder Verbindungsfehler (Exception, Status 0) = `FAIL`.
> Logik: `reachable = (httpStatus > 0)`

---

### 4. `InteractiveMenu.runEndpointCheck()` anpassen

Minimale Änderung: Die Methode ruft weiterhin `endpointCheckService.check()` auf –
da der Service intern Ping + TCP integriert, ändert sich das Interface nicht.

Ladetext anpassen: `"Prüfe Ping/TCP/HTTP für ..."` statt `"Prüfe <url> ..."`.

---

### 5. Ausgabe-Format für Zusammenfassung

Aktuell: `2 Endpoints geprüft, 1 erreichbar`

Gefordert: `Ergebnis: N/M OK`

Ein Endpoint gilt als OK wenn: `pingOk && tcpOk && httpStatus > 0`

---

## Geplante Änderungen

### Änderung 1: `EndpointCheckResult.java`

Neue Felder hinzufügen:
- `boolean pingOk`
- `long pingMs` (-1 wenn Ping fehlgeschlagen / nicht verfügbar)
- `boolean tcpOk`
- `long tcpMs` (-1 wenn TCP fehlgeschlagen / nicht verfügbar)

Bestehende Konstruktoren bleiben erhalten (pingOk=false, pingMs=-1, tcpOk=false, tcpMs=-1 als Default).
Neuer vollständiger Konstruktor wird ergänzt.

Neue Getter: `isPingOk()`, `getPingMs()`, `isTcpOk()`, `getTcpMs()`.

---

### Änderung 2: `EndpointCheckService.java`

**Intern Ping + TCP hinzufügen:**

```java
public EndpointCheckResult check(String apiName, String apiVersion, String url) {
    // 1. Host + Port aus URL extrahieren
    // 2. PingService.ping(host, DEFAULT_TIMEOUT_MS) aufrufen
    // 3. TcpCheckService.check(host, port, DEFAULT_TIMEOUT_MS) aufrufen
    // 4. HTTP HEAD/GET (wie bisher, beibehalten als Zusatzinfo)
    // 5. EndpointCheckResult mit allen Werten erstellen
}
```

`PingService` und `TcpCheckService` werden als Felder injiziert (Konstruktor oder Default-Konstruktor mit `new`).

**Port-Ermittlung:**
- URL parsen: `new URL(urlStr).getPort()` – gibt -1 zurück wenn kein expliziter Port
- Default: 443 für `https`, 80 für `http`
- Für TCP-Check wird dieser Port verwendet

---

### Änderung 3: `EndpointCheckResultFormatter.java`

**Neue Tabellenspalten:**

```
  Alias / Endpoint              Ping       TCP        HTTP Status
  MystageEndpoint (Alias)       OK  12ms   OPEN  8ms  OK (200)
  https://backend:8080/svc      FAIL  -    CLOSED  -  FAIL (timeout)
```

**Zusammenfassung:**
```
  Ergebnis: 2/3 OK
```

Kriterium für OK: `pingOk && tcpOk`

---

### Änderung 4: Tests

| Test-Klasse                             | Inhalt                                                                  |
|-----------------------------------------|-------------------------------------------------------------------------|
| `EndpointCheckResultFormatterTest.java` | Ping/TCP-Spalten; Zusammenfassung "Ergebnis: N/M OK"                    |
| `EndpointCheckServiceTest.java`         | Ping+TCP werden für erreichbare/nicht-erreichbare URLs gesetzt; HTTP-Status-Feld korrekt |
| `InteractiveMenuTest.java`              | Ausgabe nach [4]-Auswahl enthält "PING" / "TCP"                        |

---

## Reihenfolge der Implementierung

1. [ ] `PingService` – neue Überladung `ping(String host, int timeoutMs)` ergänzen
2. [ ] `TcpCheckService` – neue Überladung `check(String host, int port, int timeoutMs)` ergänzen
3. [ ] `EndpointCheckResult` – Felder `pingOk`, `pingMs`, `tcpOk`, `tcpMs` hinzufügen
4. [ ] `EndpointCheckService` – Ping + TCP intern integrieren (nutzt neue Überladungen)
5. [ ] `EndpointCheckResultFormatter` – Spalten Ping/TCP ergänzen, Zusammenfassung auf "Ergebnis: N/M OK" umstellen
6. [ ] Tests anpassen: `EndpointCheckResultFormatterTest`, `EndpointCheckServiceTest`, `PingServiceTest`, `TcpCheckServiceTest`
7. [ ] `InteractiveMenu` – Ladetext anpassen (minimal)
8. [ ] Build + alle Tests grün (`./gradlew test`)
9. [ ] Manueller Test mit echtem Server

---

## Nicht im Scope dieses Issues

- CLI-Modus (`check --server --api`): App ist rein interaktiv, bleibt so
- HTTP-Check entfernen: wird beibehalten (zusätzliche Info)
- Parallelisierung der Checks
- Paginierung
- Farb-Ausgabe / ANSI-Codes
- Persistierung von Ergebnissen
