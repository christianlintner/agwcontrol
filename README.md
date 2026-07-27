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

### 2. TCP-Check — Port-Erreichbarkeit prüfen *(geplant)*

Prüft, ob ein bestimmter Port eines AGW-Servers offen ist (TCP-Connect, vergleichbar mit `telnet host port`).

### 3. APIs auflisten *(geplant)*

Liest die verfügbaren APIs eines AGW-Servers über dessen REST-API aus.

### 4. Endpoints einer API auflisten *(geplant)*

Listet alle Endpoints (Ressourcen/Pfade) einer bestimmten API auf.

### 5. Endpoints einer API prüfen *(geplant)*

Führt für alle Endpoints einer API einen Ping- und TCP-Check durch.

---

## Konfiguration

Server werden in einer Datei `server.properties` im aktuellen Arbeitsverzeichnis konfiguriert:

```properties
server.1.host=agw-server-1.example.com
server.1.port=443

server.2.host=agw-server-2.example.com
server.2.port=443
```

> `port` ist optional und wird standardmäßig auf `443` gesetzt.

---

## Build & Ausführung

```bash
# Bauen
./gradlew jar

# Ausführen
java -jar agwcontrol.jar <befehl>
```

**Voraussetzungen:** Java 11+
