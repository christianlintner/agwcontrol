# Plan: AGW-Server-Erreichbarkeit prüfen (Ping) — Issue #1

## Überblick

Implementierung des `ping`-Befehls für das CLI-Tool `agwcontrol`.
Der Befehl liest alle konfigurierten AGW-Server aus einer `servers.properties`-Datei im
aktuellen Arbeitsverzeichnis und prüft für jeden Server, ob er per ICMP-Ping erreichbar ist
(`InetAddress.isReachable()`). Das Ergebnis wird als Tabelle auf der Konsole ausgegeben.
Der Exit-Code ist immer 0.

**CLI-Aufruf:**
```
java -jar agwcontrol.jar ping
```

**Beispiel-Ausgabe:**
```
agw-server-1.example.com | OK          |  12ms
agw-server-2.example.com | UNREACHABLE |   -
```

---

## Sub-Task 1 — Konfiguration laden: `ServerConfig` & `ConfigLoader`

**Intent:**  
Modelliert einen einzelnen Server-Eintrag aus `servers.properties` und stellt einen Mechanismus
bereit, der die Datei aus dem aktuellen Arbeitsverzeichnis liest und eine Liste von
`ServerConfig`-Objekten zurückgibt.

**Expected Outcomes:**
- `ServerConfig.java` (Datenklasse mit `host` und `port`)
- `ConfigLoader.java` (liest `./servers.properties`, parst Einträge `server.N.host` / `server.N.port`)
- Unit-Test für `ConfigLoader` mit einer Test-Properties-Datei
- Wenn `servers.properties` nicht gefunden wird, wird eine klare Fehlermeldung ausgegeben und das Programm beendet

**Todo List:**
1. Klasse `ServerConfig` anlegen: Felder `host` (String), `port` (int), Konstruktor, Getter
2. Klasse `ConfigLoader` anlegen: Methode `List<ServerConfig> load(Path file)`
3. Parsen der Properties: Schlüssel `server.N.host` und `server.N.port` zu `ServerConfig`-Objekten zusammenführen
4. Unit-Test: `ConfigLoaderTest` mit einer In-Memory-Properties-Datei (oder Temp-Datei)

**Relevant Context:**
- Package: `com.agwcontrol`
- Quelldatei: `app/src/main/java/com/agwcontrol/`
- Properties-Format laut README: `server.1.host=...`, `server.1.port=...`
- Java-Bordmittel: `java.util.Properties`, `java.nio.file.Path`

**Status:** [x] done

---

## Sub-Task 2 — Ping-Logik: `PingService`

**Intent:**  
Implementiert den eigentlichen ICMP-Ping via `InetAddress.isReachable()` mit konfigurierbarem
Timeout und misst die Antwortzeit.

**Expected Outcomes:**
- `PingResult.java` (Datenklasse: `host`, `reachable` boolean, `responseTimeMs` long / -1 wenn nicht erreichbar)
- `PingService.java` (Methode `PingResult ping(ServerConfig server)`)
- Unit-Test für `PingService` (mit gemocktem oder lokalem Loopback-Host)

**Todo List:**
1. Klasse `PingResult` anlegen: Felder `host`, `reachable`, `responseTimeMs`
2. Klasse `PingService` anlegen: Methode `PingResult ping(ServerConfig server, int timeoutMs)`
3. Implementierung mit `InetAddress.getByName(host).isReachable(timeoutMs)` + Zeitmessung via `System.currentTimeMillis()`
4. Timeout-Wert: 2000 ms als Konstante
5. Unit-Test: Ping auf `127.0.0.1` muss `reachable = true` liefern

**Relevant Context:**
- Java-Bordmittel: `java.net.InetAddress`
- README: „Ping-Prüfung: `InetAddress.isReachable` / ICMP"
- Package: `com.agwcontrol`

**Status:** [x] done

---

## Sub-Task 3 — Ausgabe: `PingResultFormatter`

**Intent:**  
Formatiert eine Liste von `PingResult`-Objekten als ausgerichtete Tabelle für die Konsole.

**Expected Outcomes:**
- `PingResultFormatter.java` (Methode `String format(List<PingResult> results)`)
- Tabellenformat: `<host> | <OK / UNREACHABLE> | <Xms / ->`
- Host-Spalte wird auf die Breite des längsten Hostnamens aufgefüllt (linksbündig)
- Unit-Test für das Format

**Todo List:**
1. Klasse `PingResultFormatter` anlegen
2. Methode `String format(List<PingResult> results)`: Spaltenbreite dynamisch aus längster `host`-Länge berechnen
3. Zeile pro Ergebnis: Hostname linksbündig, Status rechtsbündig padded, Antwortzeit rechtsbündig
4. Unit-Test mit zwei Einträgen (OK + UNREACHABLE)

**Relevant Context:**
- Package: `com.agwcontrol`
- Beispiel-Ausgabe laut Plan: `agw-server-1.example.com | OK | 12ms`

**Status:** [x] done

---

## Sub-Task 4 — CLI-Einstieg: `App.java` anpassen

**Intent:**  
Ersetzt den Hello-World-Skeleton in `App.java` durch eine echte CLI-Dispatching-Logik,
die das Argument `ping` erkennt, `ConfigLoader`, `PingService` und `PingResultFormatter`
koordiniert und das Ergebnis ausgibt.

**Expected Outcomes:**
- `App.main()` verarbeitet `args[0] == "ping"` und ruft den Ping-Flow auf
- Unbekannte Befehle geben eine Usage-Zeile aus: `Usage: agwcontrol <ping>`
- `getGreeting()` wird entfernt, `AppTest` entsprechend angepasst
- `AppTest` enthält einen dedizierten Unit-Test für den vollen Ping-Flow (mit Loopback-Host via Temp-`servers.properties`)
- `./gradlew test` läuft grün durch

**Todo List:**
1. `App.java` umschreiben: `main()` dispatcht auf `"ping"`
2. Ping-Flow: `ConfigLoader.load()` → für jeden Server `PingService.ping()` → `PingResultFormatter.format()` → `System.out.println()`
3. Fehlerfall: `servers.properties` nicht vorhanden → Fehlermeldung + Exit
4. `AppTest.java` anpassen: alten Greeting-Test entfernen
5. `AppTest` — Unit-Test für Ping-Flow: Temp-`servers.properties` mit `127.0.0.1` schreiben, `App.main(new String[]{"ping"})` aufrufen, `System.out` capturen und prüfen, dass die Ausgabe `127.0.0.1` und `OK` enthält
6. `./gradlew test` ausführen — alle Tests müssen grün sein
7. `./gradlew jar` bauen

**Relevant Context:**
- Datei: `app/src/main/java/com/agwcontrol/App.java`
- Datei: `app/src/test/java/com/agwcontrol/AppTest.java`
- `servers.properties` liegt zur Laufzeit im aktuellen Arbeitsverzeichnis (`./servers.properties`)
- Exit-Code immer 0

**Status:** [x] done
