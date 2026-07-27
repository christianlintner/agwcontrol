# agwcontrol

Kommandozeilenwerkzeug zur Überwachung von API-Gateway-Servern (AGW).  
Geschrieben in Java, gebaut mit Gradle, ausführbar als Fat-JAR.

---

## Funktionen

### 1. Ping — Server-Erreichbarkeit prüfen · [Plan](plan/ping-check-plan.md)

Prüft alle konfigurierten AGW-Server per ICMP-Ping und gibt Status und Antwortzeit als Tabelle aus.

```bash
java -jar agwcontrol.jar ping
```

```
vm30073.linux.gleis.at | OK          |  12ms
vm30074.linux.gleis.at | UNREACHABLE |   -
```

### 2. TCP-Check — Port-Erreichbarkeit prüfen · [Plan](plan/tcp-check-plan.md)

Prüft, ob der konfigurierte Port eines AGW-Servers per TCP-Connect erreichbar ist (vergleichbar mit `telnet host port`).

```bash
java -jar agwcontrol.jar tcp
```

```
vm30073.linux.gleis.at:443 | OPEN   |  45ms
vm30074.linux.gleis.at:443 | CLOSED |   -
```

### 3. APIs auflisten *(geplant)*

Liest die verfügbaren APIs eines AGW-Servers über dessen REST-API aus.

### 4. Endpoints einer API auflisten *(geplant)*

Listet alle Endpoints (Ressourcen/Pfade) einer bestimmten API auf.

### 5. Endpoints einer API prüfen *(geplant)*

Führt für alle Endpoints einer API einen Ping- und TCP-Check durch.

---

## Konfiguration

### Option A — `servers.properties` (Fallback)

Server werden in einer Datei `servers.properties` im aktuellen Arbeitsverzeichnis konfiguriert:

```properties
server.1.host=agw-server-1.example.com
server.1.port=443

server.2.host=agw-server-2.example.com
server.2.port=443
```

> `port` ist optional und wird standardmäßig auf `443` gesetzt.

### Option B — KeePass 2.x `.kdbx` · [Plan](plan/keepass-config-plan.md)

Server, Ports und Credentials können aus einer KeePass-2.x-Datenbankdatei geladen werden.

**Eintragsformat in KeePass:**

| Feld       | Inhalt                              | Beispiel                                 |
|------------|-------------------------------------|------------------------------------------|
| Title      | Hostname des Servers                | `vm40757.linux.oebb.at`                  |
| URL        | `https://<hostname>:<port>`         | `https://vm40757.linux.oebb.at:443`      |
| UserName   | Benutzername für den Server         | `agwuser`                                |
| Password   | Passwort für den Server             | `geheim`                                 |

**CLI-Aufruf mit KeePass:**

```bash
java -jar agwcontrol.jar ping --kdbx servers.kdbx --kdbx-password MeinPasswort
java -jar agwcontrol.jar tcp  --kdbx servers.kdbx --kdbx-password MeinPasswort
```

> Wird `--kdbx` nicht angegeben, wird automatisch `servers.properties` als Fallback verwendet.

---

## Build & Ausführung

```bash
# Bauen
./gradlew jar

# Ausführen
java -jar agwcontrol.jar <befehl>
```

**Voraussetzungen:** Java 11+
