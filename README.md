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

### 3. Endpoint-Check — Konnektivität eines API-Backends prüfen

Für jede ausgewählte API wird der native Backend-Endpoint ermittelt und per
DNS-Auflösung, ICMP-Ping, TCP-Connect und HTTP-GET vollständig geprüft.  
Die Ergebnisse werden tabellarisch ausgegeben und in der lokalen SQLite-Datenbank
gespeichert.

Zwei Modi stehen zur Verfügung:

| Modus | Beschreibung |
|-------|--------------|
| **Lokal** | Alle Probes werden direkt von der Client-Maschine ausgeführt. |
| **Remote via IS-Probe** | Alle Probes werden von einer webMethods Integration Server-Instanz ausgeführt, die Netzwerkzugang zu den Backends hat. Ideal, wenn der Client außerhalb des Unternehmensnetzwerks sitzt. |

### 4. APIs auflisten *(interaktiv)*

Liest die verfügbaren APIs eines AGW-Servers über dessen REST-API aus.

### 5. Report generieren

Erstellt einen HTML-Bericht auf Basis der gespeicherten Check-Ergebnisse.

```bash
java -jar agwcontrol.jar report [--db-path agwcontrol.db] [--output-dir ./reports]
```

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

**Pflichtfelder pro KeePass-Eintrag:**

| Feld       | Inhalt                              | Beispiel                                 |
|------------|-------------------------------------|------------------------------------------|
| Title      | Hostname des Servers                | `vm40757.linux.oebb.at`                  |
| URL        | `https://<hostname>:<port>`         | `https://vm40757.linux.oebb.at:443`      |
| UserName   | Benutzername für den AGW-Server     | `agwuser`                                |
| Password   | Passwort für den AGW-Server         | `geheim`                                 |

**Optionale Custom Fields pro KeePass-Eintrag:**

| Custom Field       | Inhalt                                          | Beispiel                                   |
|--------------------|-------------------------------------------------|--------------------------------------------|
| `IS-URL`           | IS-Instanz zum AGW-Server                       | `https://vm40757.linux.oebb.at:443`        |
| `CLUSTER-URL`      | Cluster-URL des API-Gateways                    | `https://apigateway-oh-dev.oebb.at`        |
| `CLUSTER-CERT-URL` | Cluster-URL mit Client-Zertifikat               | `https://apigateway-cert-oh-dev.oebb.at`   |
| `IS-PROBE-URL`     | IS-Instanz für Remote-Endpoint-Checks (Scheme + Host + Port) | `http://is-server.internal:5555` |
| `IS-PROBE-USER`    | IS-Benutzername (muss in Gruppe `Administrators` sein) | `Administrator`                    |
| `IS-PROBE-PASSWORD`| IS-Passwort                                     | `manage`                                   |

> Sind alle drei `IS-PROBE-*` Felder gesetzt, werden Endpoint-Checks für diesen Server
> automatisch remote über die IS-Instanz ausgeführt.

**CLI-Aufruf mit KeePass:**

```bash
java -jar agwcontrol.jar --kdbx servers.kdbx --kdbx-password MeinPasswort
```

> Wird `--kdbx` nicht angegeben, wird automatisch `servers.properties` als Fallback verwendet.

---

## IS-Probe — Remote-Endpoint-Checks via webMethods IS

Endpoint-Checks (DNS, Ping, TCP, HTTP) können an eine webMethods Integration Server-Instanz
delegiert werden, die im Zielnetzwerk steht. Dazu muss das IS-Paket
**`OEBB_Infra_Pro_AGWCheck`** auf der IS-Instanz installiert und der aufgerufene Benutzer
Mitglied der Gruppe `Administrators` sein.

### IS-Paket: `OEBB_Infra_Pro_AGWCheck`

Das Paket stellt folgende REST-Endpoints (RAD) unter dem Namespace
`at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD` bereit:

| Endpoint | Methode | Beschreibung |
|----------|---------|--------------|
| `/resolveHost` | GET | DNS-Auflösung eines Hostnamens |
| `/checkPing`   | GET | ICMP-Ping-Check                |
| `/checkTcp`    | GET | TCP-Connect-Check              |
| `/checkHttp`   | GET | HTTP-GET-Check                 |
| `/check`       | GET | Kombinierter Check (DNS + Ping + TCP + HTTP) |

Die OpenAPI-Spezifikation liegt unter `services/checkRAD/openapi.yaml`.

> **Hinweis:** `checkPing` verwendet intern `InetAddress.isReachable()`.
> In Docker-Umgebungen ist dafür die Linux-Capability `NET_RAW` erforderlich
> (`--cap-add NET_RAW`), da ICMP sonst geblockt wird.

### IS-Probe via CLI-Parameter

Die IS-Probe-Konfiguration kann alternativ per CLI-Argument global für alle Server
überschrieben werden:

```bash
java -jar agwcontrol.jar \
  --kdbx servers.kdbx \
  --kdbx-password MeinPasswort \
  --is-probe-url http://is-server.internal:5555 \
  --is-probe-user Administrator \
  --is-probe-password manage
```

CLI-Argumente haben **Vorrang** vor den KeePass-Custom-Fields des jeweiligen Servers.

### Priorität der IS-Probe-Konfiguration

```
CLI-Argumente (--is-probe-url / --is-probe-user / --is-probe-password)
    ↓ überschreibt
KeePass Custom Fields (IS-PROBE-URL / IS-PROBE-USER / IS-PROBE-PASSWORD)
    ↓ falls nicht vorhanden
Lokaler Check (direkt von der Client-Maschine)
```

### Vollständige CLI-Referenz

```
Usage: agwcontrol --kdbx <datei> --kdbx-password <passwort>
                  [--is-probe-url <url> --is-probe-user <user> --is-probe-password <passwort>]
       agwcontrol report [--db-path <datei>] [--output-dir <verzeichnis>]

  --kdbx               Pfad zur KeePass-2.x-.kdbx-Datei
  --kdbx-password      Master-Passwort der KeePass-Datei
  --is-probe-url       IS-Instanz für Remote-Endpoint-Checks, z.B. http://localhost:5555
  --is-probe-user      IS-Benutzername (muss in der Gruppe Administrators sein)
  --is-probe-password  IS-Passwort

  report               HTML-Berichte aus der Datenbank generieren
  --db-path            Pfad zur SQLite-Datenbank (Standard: agwcontrol.db)
  --output-dir         Ausgabeverzeichnis für Reports (Standard: aktuelles Verzeichnis)
```

---

## Build & Ausführung

```bash
# Bauen
./gradlew jar

# Ausführen (interaktiv)
java -jar agwcontrol.jar --kdbx servers.kdbx --kdbx-password MeinPasswort

# Ausführen mit IS-Probe
java -jar agwcontrol.jar \
  --kdbx servers.kdbx \
  --kdbx-password MeinPasswort \
  --is-probe-url http://is-server.internal:5555 \
  --is-probe-user Administrator \
  --is-probe-password manage

# Tests ausführen
./gradlew test
```

**Voraussetzungen:** Java 11+

---

## IS-Paket deployen

Das IS-Paket `OEBB_Infra_Pro_AGWCheck` liegt unter `services/` und kann direkt
in das IS-Packages-Verzeichnis kopiert oder über den IS-Package-Manager eingespielt werden.

```
services/
├── checkAll/flow.flow          # Kombinierter Check (DNS + Ping + TCP + HTTP)
├── checkHttp/flow.flow         # HTTP-GET-Check
├── checkPing/flow.flow         # ICMP-Ping-Check
├── checkTcp/flow.flow          # TCP-Connect-Check
├── resolveHost/flow.flow       # DNS-Auflösung
└── checkRAD/openapi.yaml       # OpenAPI 3.0.3 Spezifikation
```

**Kompatibilität:** webMethods IS 10.11 und 10.15 (Java 8 APIs).

**ACL:** Alle REST-Endpoints erfordern die Rolle `Administrators`.
