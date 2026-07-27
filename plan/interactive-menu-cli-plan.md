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

## Geplante Änderungen (Soll)

### 1. `KeePassConfigLoader` – Entry-Titel mitliefern

`ServerConfig` soll einen optionalen **Anzeigenamen** (`label`) erhalten,
der aus dem KeePass-Entry-Titel befüllt wird. Das ermöglicht eine lesbare
Server-Liste im Menü (z. B. `DN2020-DEV`).

```
ServerConfig(String label, String host, int port, String username, String password)
```

`KeePassConfigLoader.load()` befüllt `label` mit `entry.getTitle()`.

---

### 2. `InteractiveMenu` – neue Klasse

Kapselt die gesamte Menü-Logik. Abhängigkeit: `java.io.Console` / `Scanner` auf `System.in`.

**Ablauf:**
```
┌─────────────────────────────────────┐
│  AGW-Control                        │
│  ─────────────────────────────────  │
│  Verfügbare Server:                 │
│   [1]  DN2020-DEV   (10.0.0.1:443) │
│   [2]  OH-DEV       (10.0.0.2:443) │
│   [a]  Alle Server                  │
│   [q]  Beenden                      │
│                                     │
│  Auswahl: _                         │
└─────────────────────────────────────┘
```

Nach Server-Auswahl:
```
┌─────────────────────────────────────┐
│  Aktion für DN2020-DEV:             │
│   [1]  Ping                         │
│   [2]  TCP-Check                    │
│   [b]  Zurück                       │
│   [q]  Beenden                      │
│                                     │
│  Auswahl: _                         │
└─────────────────────────────────────┘
```

**Öffentliche API:**
```java
public class InteractiveMenu {
    public InteractiveMenu(List<ServerConfig> servers, InputStream in, PrintStream out) { ... }
    public void run() { ... }  // Hauptschleife
}
```

`run()` liest in einer Schleife Benutzereingaben und delegiert die Ausführung
an `PingService` bzw. `TcpCheckService`.

---

### 3. `App.java` – Umbau

Folgende Änderungen an `App.java`:

- **Entfernen:** Sub-Command-Parsing (`ping` / `tcp`), `loadFromProperties()`,
  `runPing(Path)`, `runTcpCheck(Path)`, Fallback auf `servers.properties`
- **Behalten:** `--kdbx` / `--kdbx-password` Parsing, Fehlerbehandlung beim Laden
- **Neu:** Nach erfolgreichem Laden der Server → `new InteractiveMenu(servers, System.in, System.out).run()`

Neues `main`-Skelett:
```java
public static void main(String[] args) {
    String kdbxPath = null, kdbxPassword = null;
    for (int i = 0; i < args.length - 1; i++) {
        if ("--kdbx".equals(args[i]))          kdbxPath = args[i + 1];
        if ("--kdbx-password".equals(args[i])) kdbxPassword = args[i + 1];
    }
    if (kdbxPath == null || kdbxPassword == null) { printUsage(); return; }

    List<ServerConfig> servers = loadServers(Paths.get(kdbxPath), kdbxPassword);
    if (servers == null) return;

    new InteractiveMenu(servers, System.in, System.out).run();
}
```

---

### 4. `ConfigLoader.java` – entfernen

Die Klasse `ConfigLoader` wird gelöscht, da `servers.properties` nicht mehr
unterstützt wird.

---

### 5. Tests

| Test-Klasse                   | Aktion                                                              |
|-------------------------------|---------------------------------------------------------------------|
| `AppTest.java`                | Alle Properties-basierten Tests entfernen; neue Tests für CLI-Args  |
| `ConfigLoaderTest.java`       | **Löschen**                                                         |
| `InteractiveMenuTest.java`    | **Neu** – Unit-Tests für Menü-Navigation (Mocking von InputStream)  |

**Neue `AppTest`-Szenarien:**
- `noArgsPrintsUsage` – kein Argument → Usage
- `missingPasswordPrintsUsage` – `--kdbx` ohne `--kdbx-password` → Usage
- `kdbxMissingFilePrintsError` – nicht existente Datei → Fehler auf stderr

**Neue `InteractiveMenuTest`-Szenarien:**
- Auswahl `q` → Programm beendet sich
- Auswahl `1` → Server-Untermenü erscheint
- Auswahl `1` → Ping wird ausgeführt, Ausgabe enthält Host
- Auswahl `2` → TCP-Check wird ausgeführt, Ausgabe enthält Host
- Auswahl `a` → Alle Server werden mit gewählter Aktion ausgeführt
- Ungültige Eingabe → Fehlermeldung, kein Absturz

---

## Reihenfolge der Implementierung

1. [ ] `ServerConfig` um `label`-Feld erweitern
2. [ ] `KeePassConfigLoader` befüllt `label` aus `entry.getTitle()`
3. [ ] `InteractiveMenu`-Klasse implementieren
4. [ ] `App.java` umbauen (Properties-Code entfernen, Menü starten)
5. [ ] `ConfigLoader.java` und `ConfigLoaderTest.java` löschen
6. [ ] `servers.properties` aus dem Projekt-Root löschen
7. [ ] Bestehende Tests anpassen / neue Tests schreiben
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
