# Plan: Server-Konfiguration aus KeePass-Datei laden — Issue #3

## Überblick

Erweiterung des CLI-Tools `agwcontrol` um die Möglichkeit, Server-Konfigurationen (Host, Port,
Username, Password) aus einer KeePass-2.x-Datenbankdatei (`.kdbx`) zu lesen, statt aus der
statischen `servers.properties`.

Die KeePass-Datenbankdatei und das Master-Passwort werden als CLI-Argumente übergeben.
Jeder KeePass-Eintrag liefert:
- **URL-Feld** → `hostname:port` (z. B. `vm40757.linux.oebb.at:443`)
- **Username-Feld** → Benutzername
- **Password-Feld** → Passwort

Der neue optionale CLI-Parameter `--kdbx <datei> --kdbx-password <passwort>` steht für die
Befehle `ping` und `tcp` zur Verfügung. Wird er nicht angegeben, wird wie bisher
`servers.properties` verwendet.

**CLI-Aufruf:**
```
java -jar agwcontrol.jar ping --kdbx servers.kdbx --kdbx-password MeinPasswort
java -jar agwcontrol.jar tcp  --kdbx servers.kdbx --kdbx-password MeinPasswort
```

**Bibliothek:** `de.slackspace.openkeepass` (OpenKeePass)

---

## Sub-Task 1 — `ServerConfig` um Credentials erweitern

**Intent:**
`ServerConfig` soll optionale Felder `username` und `password` aufnehmen, damit Credentials aus
der KeePass-Datei durchgereicht werden können. Bestehende Stellen, die den 2-Argument-Konstruktor
verwenden, bleiben unverändert (Rückwärtskompatibilität über Overload-Konstruktor).

**Expected Outcomes:**
- `ServerConfig` hat zwei neue optionale Felder: `username` (String, nullable) und `password` (String, nullable)
- Neuer Konstruktor: `ServerConfig(String host, int port, String username, String password)`
- Bestehender Konstruktor `ServerConfig(String host, int port)` bleibt erhalten (delegiert auf neuen Konstruktor mit `null, null`)
- Getter `getUsername()` und `getPassword()`
- Alle bestehenden Tests laufen weiterhin grün

**Todo List:**
1. Felder `username`, `password` (beide `String`, nullable) in `ServerConfig` hinzufügen
2. Neuen 4-Argument-Konstruktor anlegen
3. Bestehenden 2-Argument-Konstruktor auf `this(host, port, null, null)` delegieren
4. Getter `getUsername()` und `getPassword()` hinzufügen
5. `./gradlew test` — alle bestehenden Tests müssen grün bleiben

**Relevant Context:**
- Datei: [`ServerConfig.java`](app/src/main/java/com/agwcontrol/ServerConfig.java)
- Package: `com.agwcontrol`

**Status:** [ ] open

---

## Sub-Task 2 — Abhängigkeit: `openkeepass` in `build.gradle` eintragen

**Intent:**
Die Bibliothek `de.slackspace:openkeepass` bereitstellen, damit die KeePass-Datei gelesen
werden kann.

**Expected Outcomes:**
- `build.gradle` enthält `implementation 'de.slackspace:openkeepass:0.9.4'` (oder neueste stabile Version)
- `./gradlew dependencies` löst die Abhängigkeit ohne Fehler auf

**Todo List:**
1. In `app/build.gradle` unter `dependencies` eintragen:
   ```groovy
   implementation 'de.slackspace:openkeepass:0.9.4'
   ```
2. `./gradlew dependencies` ausführen und prüfen, dass keine Fehler auftreten

**Relevant Context:**
- Datei: [`app/build.gradle`](app/build.gradle)
- Maven-Central-Koordinate: `de.slackspace:openkeepass:0.9.4`

**Status:** [ ] open

---

## Sub-Task 3 — `KeePassConfigLoader` implementieren

**Intent:**
Neuer Loader, der eine `.kdbx`-Datei mit einem Master-Passwort öffnet und alle Einträge als
`ServerConfig`-Objekte zurückgibt. Die URL des Eintrags wird als `host:port` interpretiert.

**Expected Outcomes:**
- `KeePassConfigLoader.java` mit Methode `List<ServerConfig> load(Path kdbxFile, String masterPassword) throws IOException`
- URL-Feld des Eintrags wird nach `://` und `:` aufgesplittet → `host` und `port`
- Fehlt ein Port im URL-Feld, wird 443 als Standard verwendet
- Einträge ohne URL-Feld werden übersprungen (mit Warnung auf `System.err`)
- Falsches Master-Passwort → `IOException` mit verständlicher Meldung
- Unit-Test mit einer vorbereiteten Test-`.kdbx`-Datei in `src/test/resources/`

**Todo List:**
1. Klasse `KeePassConfigLoader` anlegen
2. `KeePassDatabase.getInstance(file, password)` öffnen (OpenKeePass-API)
3. Alle Einträge iterieren, URL parsen, `ServerConfig(host, port, username, password)` erzeugen
4. Hilfsmethode `parseHost(String url)` und `parsePort(String url)` (default 443)
5. Einträge ohne URL überspringen (Warnung auf stderr)
6. Test-`.kdbx`-Datei mit zwei Einträgen erstellen und in `src/test/resources/test.kdbx` ablegen
7. Unit-Test `KeePassConfigLoaderTest`: prüft Host, Port, Username, Password der geladenen Einträge
8. Test für falsches Passwort: erwartet `IOException`

**Relevant Context:**
- Bibliothek: `de.slackspace:openkeepass` — Einstiegspunkt: `KeePassDatabase.getInstance(InputStream, String)`
- Analoges Muster: [`ConfigLoader.java`](app/src/main/java/com/agwcontrol/ConfigLoader.java)
- Package: `com.agwcontrol`

**Status:** [ ] open

---

## Sub-Task 4 — CLI-Argument-Parsing erweitern: `App.java`

**Intent:**
`App.main()` soll die neuen Parameter `--kdbx <datei>` und `--kdbx-password <passwort>`
erkennen und bei deren Vorhandensein `KeePassConfigLoader` statt `ConfigLoader` verwenden.

**Expected Outcomes:**
- Neue Hilfsmethode `parseArgs(String[] args)` oder inline-Parsing in `main()`
- Wenn `--kdbx` übergeben wird, wird `KeePassConfigLoader.load()` verwendet
- Wenn `--kdbx-password` fehlt, aber `--kdbx` gesetzt ist, wird auf stderr ein Fehler ausgegeben
- `ping` und `tcp` unterstützen beide die neuen Argumente
- Usage-Zeile aktualisiert:
  ```
  Usage: agwcontrol <ping|tcp> [--kdbx <datei> --kdbx-password <passwort>]
  ```
- `AppTest` erhält neue Tests für den KeePass-Pfad

**Todo List:**
1. Argument-Parsing in `App.main()` erweitern: `--kdbx` und `--kdbx-password` auslesen
2. `runPing()` und `runTcpCheck()` erhalten neuen Overload / Parameter für `Optional<Path> kdbxFile` und `Optional<String> kdbxPassword`
3. Interne Auswahl: KeePass-Loader vs. Properties-Loader
4. Fehlerfall: `--kdbx` ohne `--kdbx-password` → Fehlermeldung auf stderr + return
5. Usage-Zeile in allen Ausgaben aktualisieren
6. `AppTest` — Unit-Tests für KeePass-Pfad ergänzen (Test-`.kdbx` aus `src/test/resources/` verwenden)
7. `./gradlew test` — alle Tests müssen grün sein
8. `./gradlew jar` bauen

**Relevant Context:**
- Datei: [`App.java`](app/src/main/java/com/agwcontrol/App.java)
- Datei: [`AppTest.java`](app/src/test/java/com/agwcontrol/AppTest.java)
- Neue Klasse: `KeePassConfigLoader`

**Status:** [ ] open

---

## Sub-Task 5 — README erweitern

**Intent:**
Dokumentiert das neue KeePass-Feature im README: Voraussetzungen, CLI-Aufruf, KeePass-Eintragsformat.

**Expected Outcomes:**
- Neuer Abschnitt „Konfiguration via KeePass" im README
- Beschreibung des erwarteten Eintragsformats (URL = `host:port`, Username, Password)
- CLI-Aufruf-Beispiele für `ping` und `tcp` mit `--kdbx`-Parametern
- Hinweis, dass `servers.properties` weiterhin als Fallback funktioniert

**Todo List:**
1. Abschnitt „Konfiguration via KeePass (.kdbx)" in `README.md` ergänzen
2. Eintragsformat-Tabelle oder Beschreibung (URL, Username, Password)
3. CLI-Beispiele einfügen
4. Hinweis auf Fallback `servers.properties`

**Relevant Context:**
- Datei: [`README.md`](README.md)
- Vorlage: bestehende Abschnitte in `README.md`

**Status:** [ ] open
