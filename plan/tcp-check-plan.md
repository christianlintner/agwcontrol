# Plan: TCP-Port-Erreichbarkeit prüfen — Issue #2

## Überblick

Implementierung des `tcp`-Befehls für das CLI-Tool `agwcontrol`.
Der Befehl liest alle konfigurierten AGW-Server aus `servers.properties` im aktuellen
Arbeitsverzeichnis und prüft für jeden Server, ob der konfigurierte Port per TCP-Connect
erreichbar ist (`Socket.connect()`). Das Ergebnis wird als Tabelle auf der Konsole ausgegeben.
Der Exit-Code ist immer 0.

**CLI-Aufruf:**
```
java -jar agwcontrol.jar tcp
```

**Beispiel-Ausgabe:**
```
vm30073.linux.gleis.at | OPEN   |  45ms
vm30074.linux.gleis.at | CLOSED |   -
```

---

## Sub-Task 1 — Ergebnis-Datenklasse: `TcpCheckResult`

**Intent:**
Modelliert das Ergebnis einer einzelnen TCP-Verbindungsprüfung analog zu `PingResult`.

**Expected Outcomes:**
- `TcpCheckResult.java` mit Feldern `host` (String), `port` (int), `open` (boolean), `responseTimeMs` (long, -1 wenn nicht erreichbar)
- Konstruktor + Getter

**Todo List:**
1. Klasse `TcpCheckResult` im Package `com.agwcontrol` anlegen
2. Felder: `host`, `port`, `open`, `responseTimeMs`
3. Konstruktor und Getter implementieren

**Relevant Context:**
- Analoges Muster: [`PingResult.java`](app/src/main/java/com/agwcontrol/PingResult.java)
- Package: `com.agwcontrol`

**Status:** [x] done

---

## Sub-Task 2 — TCP-Logik: `TcpCheckService`

**Intent:**
Implementiert die eigentliche TCP-Port-Prüfung via `Socket.connect()` mit konfigurierbarem
Timeout und Zeitmessung — analog zu `PingService`.

**Expected Outcomes:**
- `TcpCheckService.java` mit Methode `TcpCheckResult check(ServerConfig server)` und Overload mit `timeoutMs`
- Konstante `DEFAULT_TIMEOUT_MS = 2000`
- Bei Erfolg: `open = true`, `responseTimeMs` = gemessene Verbindungszeit
- Bei Fehler / Timeout: `open = false`, `responseTimeMs = -1`
- Unit-Test: TCP-Connect auf `127.0.0.1:80` — da Port 80 typischerweise geschlossen ist, wird `open = false` erwartet; alternativ wird ein freier Port per `ServerSocket` in einer Helferklasse geöffnet, um `open = true` zu testen

**Todo List:**
1. Klasse `TcpCheckService` anlegen
2. Methode `TcpCheckResult check(ServerConfig server, int timeoutMs)`: `new Socket()` → `socket.connect(new InetSocketAddress(host, port), timeoutMs)` in try-with-resources
3. Zeitmessung via `System.currentTimeMillis()`
4. Bei `IOException` oder Timeout: `open = false`, `responseTimeMs = -1`
5. Unit-Test `TcpCheckServiceTest`: Loopback-Server mit `ServerSocket` auf zufälligem Port öffnen → `check()` aufrufen → `open = true` und `responseTimeMs >= 0` prüfen

**Relevant Context:**
- Java-Bordmittel: `java.net.Socket`, `java.net.InetSocketAddress`, `java.net.ServerSocket`
- Analoges Muster: [`PingService.java`](app/src/main/java/com/agwcontrol/PingService.java)
- `ServerConfig.getPort()` liefert den zu prüfenden Port

**Status:** [x] done

---

## Sub-Task 3 — Ausgabe: `TcpCheckResultFormatter`

**Intent:**
Formatiert eine Liste von `TcpCheckResult`-Objekten als ausgerichtete Tabelle für die Konsole
— analog zu `PingResultFormatter`, aber mit Statuswörtern `OPEN` / `CLOSED` und Anzeige von Host + Port.

**Expected Outcomes:**
- `TcpCheckResultFormatter.java` mit Methode `String format(List<TcpCheckResult> results)`
- Tabellenformat: `<host:port> | <OPEN / CLOSED> | <Xms / ->`
- Host:Port-Spalte linksbündig auf Breite des längsten Eintrags aufgefüllt
- Status-Spalte auf 6 Zeichen rechtsbündig padded (`OPEN` / `CLOSED`)
- Zeitangabe rechtsbündig (5 Zeichen)
- Unit-Test mit zwei Einträgen (OPEN + CLOSED)

**Todo List:**
1. Klasse `TcpCheckResultFormatter` anlegen
2. Methode `String format(List<TcpCheckResult> results)`: Spaltenbreite aus längstem `host:port`-String berechnen
3. Zeile pro Ergebnis mit `String.format()` formatieren
4. Unit-Test `TcpCheckResultFormatterTest` mit zwei Einträgen

**Relevant Context:**
- Analoges Muster: [`PingResultFormatter.java`](app/src/main/java/com/agwcontrol/PingResultFormatter.java)
- Package: `com.agwcontrol`

**Status:** [x] done

---

## Sub-Task 4 — CLI-Einstieg: `App.java` erweitern

**Intent:**
Erweitert den Dispatch in `App.main()` um den Befehl `tcp` und ergänzt die Usage-Zeile.

**Expected Outcomes:**
- `App.main()` verarbeitet `args[0] == "tcp"` und ruft `runTcpCheck()` auf
- `runTcpCheck(Path configFile)` koordiniert `ConfigLoader` → `TcpCheckService` → `TcpCheckResultFormatter`
- Usage-Zeile aktualisiert: `Usage: agwcontrol <ping|tcp>`
- `AppTest` erhält einen dedizierten Unit-Test für den TCP-Flow (Loopback-`ServerSocket`, Temp-`servers.properties`)
- `./gradlew test` läuft grün durch

**Todo List:**
1. `switch`-Statement in `App.main()` um `case "tcp"` ergänzen
2. Methode `runTcpCheck(Path configFile)` analog zu `runPing()` implementieren
3. Usage-Zeile in allen Ausgaben auf `<ping|tcp>` aktualisieren
4. `AppTest` — Unit-Test für TCP-Flow: `ServerSocket` auf zufälligem Port, Temp-`servers.properties` mit `127.0.0.1` und diesem Port, `App.main(new String[]{"tcp"})` aufrufen, `System.out` capturen, Ausgabe auf `OPEN` prüfen
5. `./gradlew test` ausführen — alle Tests müssen grün sein
6. `./gradlew jar` bauen

**Relevant Context:**
- Datei: [`App.java`](app/src/main/java/com/agwcontrol/App.java)
- Datei: [`AppTest.java`](app/src/test/java/com/agwcontrol/AppTest.java)
- Muster: `runPing()` in `App.java` als Vorlage

**Status:** [x] done

---

## Sub-Task 5 — README erweitern

**Intent:**
Dokumentiert den neuen `tcp`-Befehl im README und markiert Feature #2 als implementiert.

**Expected Outcomes:**
- Abschnitt „2. TCP-Check" im README ist vollständig ausformuliert (kein *(geplant)* mehr)
- CLI-Aufruf und Beispiel-Ausgabe sind dokumentiert
- Konfigurationsabschnitt verweist auf den Plan

**Todo List:**
1. Abschnitt `### 2. TCP-Check` im README vollständig ausformulieren: Beschreibung, CLI-Aufruf, Beispiel-Ausgabe, Link auf `plan/tcp-check-plan.md`
2. *(geplant)* aus der Überschrift entfernen

**Relevant Context:**
- Datei: [`README.md`](README.md)
- Vorlage: Abschnitt „1. Ping" im README

**Status:** [x] done
