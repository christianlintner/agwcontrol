# Plan: Issue #6 – Interactive menu-driven CLI

Branch: `feat/issue-6-interactive-menu-cli`

---

## Ziel

Die bisherige parameterbasierte Aufruf-Methode (`agwcontrol ping|tcp [--kdbx ...]`)
wird durch ein interaktives Menü ersetzt. Der Programmstart erfolgt ausschließlich
mit `--kdbx` / `--kdbx-password`. Die Unterstützung für `servers.properties` entfällt.

Neuer Aufruf:
```bash
java -jar agwcontrol.jar --kdbx servers.kdbx --kdbx-password MeinPasswort
```

---

## Aktueller Zustand (Ist)

| Klasse / Datei              | Relevanz für das Issue                                        |
|-----------------------------|---------------------------------------------------------------|
| `App.java`                  | Parst Sub-Commands (`ping`, `tcp`), ruft Services direkt auf  |
| `ConfigLoader.java`         | Lädt `servers.properties` – **entfällt**                      |
| `KeePassConfigLoader.java`  | Lädt Server aus `.kdbx` – **bleibt, leicht erweitert**        |
| `ServerConfig.java`         | Hält Host, Port, Username, Password – **bleibt unverändert**  |
| `PingService.java`          | Ping-Logik – **bleibt unverändert**                           |
| `TcpCheckService.java`      | TCP-Logik – **bleibt unverändert**                            |
| `PingResultFormatter.java`  | Ausgabe-Formatierung – **bleibt unverändert**                 |
| `TcpCheckResultFormatter.java` | Ausgabe-Formatierung – **bleibt unverändert**              |
| `servers.properties`        | Konfigurations-Fallback – **entfällt**                        |

---

## KeePass-Dateistruktur (ermittelt)

```
Passwörter
  └── AGW-Server
        ├── DN2020-DEV   → 1 Eintrag  (vm40757.linux.oebb.at:443)
        ├── OH-DEV       → 3 Einträge (vm40205, vm40206, vm40207)
        ├── DN-PREPROD   → 3 Einträge (vm40754, vm40755, vm40756)
        └── OH-PREPROD   → 3 Einträge (vm30073, vm30074, vm30075)
```

**Schlussfolgerung:** Eine **Umgebung** entspricht einer KeePass-**Gruppe** mit 1–n Server-Einträgen.
Das Menü zeigt Gruppen, nicht einzelne Einträge. Aktionen werden auf **alle Server der Gruppe** angewendet.

---

## Geplante Änderungen (Soll)

### 1. Neues Datenmodell: `ServerGroup`

Anstatt `ServerConfig` ein `label`-Feld zu verpassen, wird eine neue Klasse
`ServerGroup` eingeführt, die eine Umgebung (= KeePass-Gruppe) abbildet:

```java
public class ServerGroup {
    private final String name;                   // z. B. "OH-DEV"
    private final List<ServerConfig> servers;    // alle Einträge der Gruppe
}
```

`ServerConfig` bleibt unverändert.

---

### 2. `KeePassConfigLoader` – gruppiert laden

Neue Methode (oder Umbau der bestehenden):

```java
public List<ServerGroup> loadGroups(Path kdbxFile, String masterPassword) throws IOException
```

Iteriert über die **Sub-Gruppen** von `AGW-Server` (statt über alle flachen Einträge)
und baut pro Gruppe ein `ServerGroup`-Objekt. Einträge ohne URL werden
weiterhin mit Warnung übersprungen.

Die alte `load()`-Methode (flache Liste) **entfällt** (wird nicht mehr benötigt).

---

### 3. `InteractiveMenu` – neue Klasse

Kapselt die gesamte Menü-Logik. Abhängigkeit: `java.util.Scanner` auf `System.in`.

Eingabe: `List<ServerGroup>`

**Hauptmenü – Gruppen-Auswahl:**
```
AGW-Control
─────────────────────────────────────
Verfügbare Umgebungen:
  [1]  DN2020-DEV    (1 Server)
  [2]  OH-DEV        (3 Server)
  [3]  DN-PREPROD    (3 Server)
  [4]  OH-PREPROD    (3 Server)
  [a]  Alle Umgebungen
  [q]  Beenden

Auswahl: _
```

**Untermenü – Aktion:**
```
Aktion für OH-DEV (3 Server):
  [1]  Ping
  [2]  TCP-Check
  [b]  Zurück
  [q]  Beenden

Auswahl: _
```

Aktionen werden auf **alle Server der gewählten Gruppe(n)** angewendet.

**Öffentliche API:**
```java
public class InteractiveMenu {
    public InteractiveMenu(List<ServerGroup> groups, InputStream in, PrintStream out) { ... }
    public void run() { ... }  // Hauptschleife
}
```

---

### 4. `App.java` – Umbau

Folgende Änderungen an `App.java`:

- **Entfernen:** Sub-Command-Parsing (`ping` / `tcp`), `loadFromProperties()`,
  `runPing(Path)`, `runTcpCheck(Path)`, Fallback auf `servers.properties`
- **Behalten:** `--kdbx` / `--kdbx-password` Parsing, Fehlerbehandlung beim Laden
- **Neu:** Nach erfolgreichem Laden → `new InteractiveMenu(groups, System.in, System.out).run()`

Neues `main`-Skelett:
```java
public static void main(String[] args) {
    String kdbxPath = null, kdbxPassword = null;
    for (int i = 0; i < args.length - 1; i++) {
        if ("--kdbx".equals(args[i]))          kdbxPath = args[i + 1];
        if ("--kdbx-password".equals(args[i])) kdbxPassword = args[i + 1];
    }
    if (kdbxPath == null || kdbxPassword == null) { printUsage(); return; }

    List<ServerGroup> groups = loadGroups(Paths.get(kdbxPath), kdbxPassword);
    if (groups == null) return;

    new InteractiveMenu(groups, System.in, System.out).run();
}
```

---

### 5. `ConfigLoader.java` – entfernen

Die Klasse `ConfigLoader` wird gelöscht, da `servers.properties` nicht mehr
unterstützt wird.

---

### 6. Tests

| Test-Klasse                        | Aktion                                                                  |
|------------------------------------|-------------------------------------------------------------------------|
| `AppTest.java`                     | Alle Properties-basierten Tests entfernen; neue Tests für CLI-Args      |
| `ConfigLoaderTest.java`            | **Löschen**                                                             |
| `KeePassConfigLoaderTest.java`     | `loadsAllEntries` → `loadsAllGroups`; Gruppen-Struktur prüfen           |
| `InteractiveMenuTest.java`         | **Neu** – Unit-Tests für Menü-Navigation (Mocking von InputStream)      |

**Neue `AppTest`-Szenarien:**
- `noArgsPrintsUsage` – kein Argument → Usage
- `missingPasswordPrintsUsage` – `--kdbx` ohne `--kdbx-password` → Usage
- `kdbxMissingFilePrintsError` – nicht existente Datei → Fehler auf stderr

**Neue `KeePassConfigLoaderTest`-Szenarien:**
- `loadsCorrectNumberOfGroups` – 4 Gruppen erwartet
- `ohDevGroupHasThreeServers` – Gruppe `OH-DEV` hat genau 3 `ServerConfig`-Einträge
- `dn2020DevGroupHasOneServer` – Gruppe `DN2020-DEV` hat genau 1 `ServerConfig`-Eintrag
- `groupNameIsPreserved` – `ServerGroup.getName()` liefert den KeePass-Gruppennamen

**Neue `InteractiveMenuTest`-Szenarien:**
- Auswahl `q` → Programm beendet sich
- Auswahl `1` → Gruppen-Untermenü erscheint (Aktion-Auswahl)
- Auswahl `1` im Untermenü → Ping auf alle Server der Gruppe, Ausgabe enthält alle Hosts
- Auswahl `2` im Untermenü → TCP-Check auf alle Server der Gruppe
- Auswahl `a` im Hauptmenü → alle Gruppen mit gewählter Aktion
- Ungültige Eingabe → Fehlermeldung, kein Absturz

---

## Reihenfolge der Implementierung

1. [ ] Neue Klasse `ServerGroup` anlegen
2. [ ] `KeePassConfigLoader` – `loadGroups()` implementieren (gruppenweise laden)
3. [ ] `InteractiveMenu`-Klasse implementieren (arbeitet mit `List<ServerGroup>`)
4. [ ] `App.java` umbauen (Properties-Code entfernen, Menü starten)
5. [ ] `ConfigLoader.java` und `ConfigLoaderTest.java` löschen
6. [ ] `servers.properties` aus dem Projekt-Root löschen
7. [ ] Tests anpassen: `KeePassConfigLoaderTest` + `AppTest` + neuer `InteractiveMenuTest`
8. [ ] Build + alle Tests grün (`./gradlew test`)
9. [ ] Fat-JAR bauen und manuell testen (`java -jar agwcontrol.jar ...`)

---

## Abhängigkeiten / Bibliotheken

Keine neuen Abhängigkeiten erforderlich. Die Menü-Interaktion erfolgt
ausschließlich über `java.util.Scanner` (Standard-JDK).

---

## Nicht im Scope dieses Issues

- Farb-Ausgabe / ANSI-Escape-Codes
- Konfiguration von Timeout-Werten
- Logging-Framework
