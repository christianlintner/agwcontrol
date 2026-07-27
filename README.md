# agwcontrol

Kommandozeilenwerkzeug zur Überwachung und Prüfung von API-Gateway-Servern (AGW).  
Geschrieben in Java, gebaut mit Gradle, ausführbar als Fat-JAR.

---

## Funktionen

### 1. AGW-Server-Erreichbarkeit prüfen (Ping)
Prüft, ob ein AGW-Server per ICMP-Ping erreichbar ist.  
Die zu prüfenden Server werden über eine **Property-Datei** konfiguriert.

### 2. AGW-Server-Endpoint-Erreichbarkeit prüfen (TCP/Telnet)
Prüft, ob ein bestimmter Port eines AGW-Servers offen und erreichbar ist (TCP-Connect, vergleichbar mit `telnet host port`).

### 3. APIs des AGW-Servers auflisten
Liest die verfügbaren APIs eines AGW-Servers über dessen API aus und gibt sie als Liste aus.

### 4. Endpoints einer API auflisten
Listet alle Endpoints (Ressourcen/Pfade) einer bestimmten API auf dem AGW-Server auf.

### 5. Endpoints einer API prüfen (Ping + TCP)
Führt für die Endpoints einer API sowohl einen Ping- als auch einen TCP-Port-Check durch und gibt den Status jedes Endpoints aus.

---

## Konfiguration

Die Server werden über eine **Property-Datei** (`servers.properties`) konfiguriert.  
Beispiel:

```properties
# AGW-Server Konfiguration
server.1.host=agw-server-1.example.com
server.1.port=8443

server.2.host=agw-server-2.example.com
server.2.port=8443
```

---

## Build & Ausführung

### Voraussetzungen
- Java 11+
- Gradle (oder `./gradlew`)

### Bauen

```bash
./gradlew jar
```

Das Fat-JAR `agwcontrol.jar` wird direkt im Projektverzeichnis abgelegt.

### Ausführen

```bash
java -jar agwcontrol.jar [Optionen]
```

---

## Projektstruktur

```
agwcontrol/
├── app/
│   └── src/main/java/com/agwcontrol/
│       └── App.java          # Einstiegspunkt
├── agwcontrol.jar            # Ausführbares Fat-JAR
├── servers.properties        # Serverkonfiguration (Property-Datei)
├── build.gradle
└── settings.gradle
```

---

## Technologie

| Komponente     | Technologie              |
|----------------|--------------------------|
| Sprache        | Java 11                  |
| Build-Tool     | Gradle                   |
| Testing        | JUnit Jupiter            |
| Ping-Prüfung   | `InetAddress.isReachable` / ICMP |
| Port-Prüfung   | TCP-Socket-Connect       |
| AGW-API-Zugriff| HTTP(S) REST             |
