# Issue #19 – Ping & TCO-Check: AGW und IS für alle Umgebungen mit LB- und Node-Adressen prüfen

## Ziel

Ping-Check und TCP-Check (TCO-Check) prüfen pro KeePass-Eintrag **vier Adressen**:

| Label | Quelle im KeePass-Eintrag | Beschreibung |
|---|---|---|
| `AGW` | Eintrag-URL (Pflichtfeld) | AGW-Node-Adresse (z. B. `apigateway-oh-preprod-gw1.oebb.at`) |
| `CLUSTER` | Custom Field `CLUSTER-URL` | Zentrale AGW-Cluster-LB-Adresse (external) |
| `CLUSTER-CERT` | Custom Field `CLUSTER-CERT-URL` | Zentrale AGW-Cluster-LB-Adresse (internal_cert) |
| `IS` | Custom Field `IS-URL` | IS-REST-API-Adresse des Nodes |

Die Custom Fields sind **optional** – fehlen sie im Eintrag, wird der jeweilige Check übersprungen.

---

## Ist-Zustand

### KeePass-Struktur
- Pro Umgebung gibt es eine Gruppe unter `AGW-Server`.
- Jeder Eintrag hat eine URL (→ AGW-Node-Host) und optional:
  - Custom Field `IS-URL` (→ IS-REST-API-Endpunkt des Nodes)
- Aktuell **keine** Custom Fields für die zentralen Cluster-LB-Adressen.

### Ping (`PingService`, `InteractiveMenu.runPing`)
- Iteriert über `ServerConfig`-Liste und pingt nur `server.getHost()` (= AGW-Node-Host).
- IS-Host, CLUSTER-URL, CLUSTER-CERT-URL werden **nicht** gepingt.

### TCP-Check (`TcpCheckService`, `InteractiveMenu.runTcpCheck`)
- Analog: prüft nur `server.getHost():server.getPort()`.
- IS-Host, CLUSTER-URL, CLUSTER-CERT-URL werden **nicht** geprüft.

### `ServerConfig`
- Felder: `host`, `port`, `username`, `password`, `isUrl`
- `isUrl` enthält die vollständige IS-URL des Nodes.

---

## Entscheidung: KeePass-Struktur

Jeder **Node-Eintrag** in der KeePass-Datei erhält zwei neue Custom Fields:

| Custom Field | Beispielwert | Bedeutung |
|---|---|---|
| `CLUSTER-URL` | `https://apigateway-oh-preprod.oebb.at` | Zentrale LB-Adresse (external) |
| `CLUSTER-CERT-URL` | `https://apigateway-cert-oh-preprod.oebb.at` | Zentrale LB-Adresse (internal_cert) |

Diese Felder sind **optional** – fehlen sie, wird kein Check dafür durchgeführt.

> **Hinweis:** Die `CLUSTER-CERT-URL` kann zunächst leer bleiben, bis die Klärung der `internal_cert`-Adresse abgeschlossen ist.

### Warum Custom Fields pro Node statt eigener Einträge?

- Cluster-LB-URLs gehören logisch zu den Nodes (jeder Node kennt seine LB).
- Kein redundantes Anlegen von separaten LB-Einträgen mit identischen Credentials.
- Der Code kann direkt aus `ServerConfig` alle zu prüfenden Adressen ableiten.
- Gleiche Credentials (Username/Password) wie der Node-Eintrag werden wiederverwendet.

---

## Anforderungen

1. **Ping** prüft pro Server-Eintrag:
   - AGW-Node-Host (aus `host`, bereits vorhanden)
   - IS-Host (aus `isUrl`, falls gesetzt)
   - Cluster-LB-Host (aus `clusterUrl`, falls gesetzt)
   - Cluster-Cert-LB-Host (aus `clusterCertUrl`, falls gesetzt)

2. **TCP-Check** prüft pro Server-Eintrag:
   - AGW-Node-Host:Port (bereits vorhanden)
   - IS-Host:Port (aus `isUrl`, falls gesetzt)
   - Cluster-LB-Host:Port (aus `clusterUrl`, falls gesetzt)
   - Cluster-Cert-LB-Host:Port (aus `clusterCertUrl`, falls gesetzt)

3. **Ausgabe** im Terminal bleibt übersichtlich:
   - Jede geprüfte Adresse erhält ein Label: `AGW`, `IS`, `CLUSTER`, `CLUSTER-CERT`.
   - Breite der Host-Spalte passt sich dynamisch an.

---

## Lösungsansatz

### 1. `ServerConfig` – zwei neue Felder

```java
public class ServerConfig {
    // bestehend
    private final String host;
    private final int    port;
    private final String username;
    private final String password;
    private final String isUrl;

    // neu
    private final String clusterUrl;      // CLUSTER-URL
    private final String clusterCertUrl;  // CLUSTER-CERT-URL
}
```

Neuer vollständiger Konstruktor:
```java
public ServerConfig(String host, int port, String username, String password,
                    String isUrl, String clusterUrl, String clusterCertUrl)
```

Bestehende Konstruktoren delegieren mit `null, null` für die neuen Felder → kein Breaking Change.

### 2. `KeePassConfigLoader` – zwei neue Custom Fields lesen

In `entriesFromGroup()` analog zu `IS-URL`:
```java
String clusterUrl     = readCustomField(entry, "CLUSTER-URL");
String clusterCertUrl = readCustomField(entry, "CLUSTER-CERT-URL");
servers.add(new ServerConfig(host, port, username, password, isUrl, clusterUrl, clusterCertUrl));
```

### 3. `PingResult` / `TcpCheckResult` – Label-Feld

Um AGW, IS, CLUSTER und CLUSTER-CERT in der Ausgabe unterscheiden zu können:

```java
// PingResult – neuer Konstruktor mit Label
public PingResult(String host, String label, boolean reachable, long responseTimeMs)
```

Label-Werte: `"AGW"`, `"IS"`, `"CLUSTER"`, `"CLUSTER-CERT"` (oder `null` für Abwärtskompatibilität).

### 4. `PingService` – `pingAll(ServerConfig)`

```java
public List<PingResult> pingAll(ServerConfig server) {
    List<PingResult> results = new ArrayList<>();
    results.add(ping(server.getHost(), TIMEOUT).withLabel("AGW"));
    if (server.getIsUrl() != null)
        results.add(ping(hostFrom(server.getIsUrl()), TIMEOUT).withLabel("IS"));
    if (server.getClusterUrl() != null)
        results.add(ping(hostFrom(server.getClusterUrl()), TIMEOUT).withLabel("CLUSTER"));
    if (server.getClusterCertUrl() != null)
        results.add(ping(hostFrom(server.getClusterCertUrl()), TIMEOUT).withLabel("CLUSTER-CERT"));
    return results;
}
```

### 5. `TcpCheckService` – `checkAll(ServerConfig)`

Analog zu `pingAll`, mit Host+Port aus der jeweiligen URL.

### 6. `PingResultFormatter` / `TcpCheckResultFormatter` – Label-Spalte

Wenn mind. ein Ergebnis ein Label hat, wird eine zusätzliche Spalte ausgegeben:
```
apigateway-oh-preprod-gw1.oebb.at | AGW          | OK          |   42ms
apigateway-oh-preprod-is1.oebb.at | IS           | OK          |   38ms
apigateway-oh-preprod.oebb.at     | CLUSTER      | OK          |   40ms
apigateway-cert-oh-preprod.oebb.at| CLUSTER-CERT | UNREACHABLE |     -
```

### 7. `InteractiveMenu` – `runPing` / `runTcpCheck`

Ersetzen `ping(server)` / `check(server)` durch `pingAll(server)` / `checkAll(server)`.

---

## Detaillierte Änderungen

| Datei | Änderungstyp | Beschreibung |
|-------|-------------|--------------|
| `ServerConfig.java` | Erweiterung | Felder `clusterUrl`, `clusterCertUrl`; neuer 7-arg-Konstruktor |
| `KeePassConfigLoader.java` | Erweiterung | Custom Fields `CLUSTER-URL` und `CLUSTER-CERT-URL` lesen |
| `PingResult.java` | Erweiterung | Optionales `label`-Feld; neuer Konstruktor + `withLabel()` |
| `PingResultFormatter.java` | Erweiterung | Label-Spalte wenn mind. ein Label gesetzt |
| `PingService.java` | Erweiterung | `pingAll(ServerConfig)` → `List<PingResult>` |
| `TcpCheckResult.java` | Erweiterung | Optionales `label`-Feld; neuer Konstruktor + `withLabel()` |
| `TcpCheckResultFormatter.java` | Erweiterung | Label-Spalte in Ausgabe |
| `TcpCheckService.java` | Erweiterung | `checkAll(ServerConfig)` → `List<TcpCheckResult>` |
| `InteractiveMenu.java` | Anpassung | `runPing` / `runTcpCheck` verwenden `…All()`-Methoden |

---

## Tests

| Test-Klasse | Neue Tests |
|------------|-----------|
| `KeePassConfigLoaderTest.java` | `parsesClusterUrl()`, `parsesClusterCertUrl()`, `missingClusterUrlReturnsNull()` |
| `PingServiceTest.java` | `pingAllIncludesAllUrls()`, `pingAllSkipsMissingUrls()` |
| `TcpCheckServiceTest.java` | `checkAllIncludesAllUrls()`, `checkAllSkipsMissingUrls()` |
| `PingResultFormatterTest.java` | `formatShowsLabelColumnWhenAnyLabelSet()` |
| `TcpCheckResultFormatterTest.java` | `formatShowsLabelColumnWhenAnyLabelSet()` |
| `InteractiveMenuTest.java` | Bestehende Tests bleiben grün (kein Breaking Change) |

> **Hinweis:** Die `test.kdbx` muss für die neuen `KeePassConfigLoaderTest`-Tests um mindestens einen Eintrag mit `CLUSTER-URL` erweitert werden.

---

## KeePass-Aufgaben (manuell)

- [ ] Für jeden bestehenden AGW-Node-Eintrag: Custom Field `CLUSTER-URL` mit der zugehörigen externen LB-Adresse befüllen.
- [ ] Custom Field `CLUSTER-CERT-URL` anlegen, aber vorerst leer lassen (Klärung steht aus).
- [ ] Sobald `internal_cert`-Adresse geklärt: `CLUSTER-CERT-URL` befüllen.

---

## Akzeptanzkriterien (aus Issue)

- [ ] Ping-Check prüft AGW-Node, IS, CLUSTER und CLUSTER-CERT pro Eintrag
- [ ] TCP-Check prüft AGW-Node, IS, CLUSTER und CLUSTER-CERT pro Eintrag
- [ ] `CLUSTER-URL` Custom Field in KeePass für alle Node-Einträge vorhanden
- [ ] Verhalten der `internal_cert`-Adresse ist geklärt und `CLUSTER-CERT-URL` befüllt
