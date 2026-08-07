# Plan: DNS-Auflösung via IS-Service resolveHost – IP in DB + Report

**GitHub Issue:** #26
**Branch:** `feat/issue-26-dns-resolvehost-ip`

## Ziel

Beim Auflisten von Endpoints soll der Hostname der `resolved_url` über den bestehenden IS-Service `resolveHost` aufgelöst werden. Die DNS-Auflösung findet auf dem IS-Server statt (gleiche Netzwerkrouten wie das API Gateway). Die ermittelte IP-Adresse wird in der DB persistiert und im pro-Umgebungs-CSV-Report als zusätzliche Spalte ausgegeben.

## Scope

Betroffen sind ausschließlich:
- `RoutingEndpoint` (neues Feld)
- `ApiDatabase` (Schema + CRUD)
- `IsEndpointCheckService` (neuer IS-Aufruf)
- `AgwApiService` (DNS-Trigger nach Server-Abruf)
- `InteractiveMenu` (Ausgabe)
- `DbReportService` (CSV-Spalte)
- Zugehörige Tests

Nicht betroffen: `EndpointCheckService` (lokal), Cross-Env-Report, API-Comparison-Report.

---

## Sub-Tasks

---

### Sub-Task 1: `RoutingEndpoint` – Feld `resolvedIp` hinzufügen

**Status:** [x] done

**Intent**
Das Datenmodell muss die aufgelöste IP-Adresse tragen können, damit sie durch alle Schichten (Service → DB → Report) weitergereicht werden kann.

**Expected Outcomes**
- `RoutingEndpoint` hat ein Feld `resolvedIp` (String, nullable)
- Getter `getResolvedIp()` vorhanden
- Setter `setResolvedIp(String ip)` vorhanden
- Die Factory-Methoden `direct()` und `alias()` initialisieren `resolvedIp` mit `null`

**Todo List**
1. Feld `private String resolvedIp` zu `RoutingEndpoint` hinzufügen
2. Getter `getResolvedIp()` hinzufügen
3. Setter `setResolvedIp(String ip)` hinzufügen

**Relevant Context**
- [`RoutingEndpoint.java`](app/src/main/java/com/agwcontrol/RoutingEndpoint.java)
- Bestehende Factory-Methoden `direct()` und `alias()` müssen nicht geändert werden – Feld startet als `null`

---

### Sub-Task 2: `ApiDatabase` – Spalte `resolved_ip` + Schema-Migration

**Status:** [x] done

**Intent**
Die DB-Tabelle `endpoints` muss die IP-Adresse speichern. Da bestehende DBs (z.B. `agwcontrol.db`) die Spalte noch nicht haben, muss die Migration idempotent sein.

**Expected Outcomes**
- Tabelle `endpoints` hat Spalte `resolved_ip TEXT`
- `initSchema()` führt `ALTER TABLE endpoints ADD COLUMN resolved_ip TEXT` aus, ohne bei bereits vorhandener Spalte zu werfen
- `saveEndpoints()` schreibt `ep.getResolvedIp()` in die neue Spalte
- `loadEndpoints()` liest `resolved_ip` und setzt sie via `setResolvedIp()` auf dem geladenen Objekt

**Todo List**
1. In `initSchema()`: nach dem `CREATE TABLE IF NOT EXISTS endpoints`-Block ein `ALTER TABLE endpoints ADD COLUMN resolved_ip TEXT` einfügen, in einem try/catch der `SQLException` mit Message `"duplicate column"` ignoriert
2. In `saveEndpoints()`: INSERT-SQL um `resolved_ip` erweitern, `ps.setString(n, ep.getResolvedIp())` hinzufügen
3. In `loadEndpoints()`: SELECT-SQL um `resolved_ip` erweitern, nach dem Erstellen des `RoutingEndpoint`-Objekts `ep.setResolvedIp(rs.getString("resolved_ip"))` aufrufen

**Relevant Context**
- [`ApiDatabase.java`](app/src/main/java/com/agwcontrol/ApiDatabase.java) – `initSchema()` Zeile 32, `saveEndpoints()` Zeile 143, `loadEndpoints()` Zeile 180
- PRIMARY KEY der `endpoints`-Tabelle: `(environment, api_id, alias_name, resolved_url)` – keine Änderung nötig
- Tests nutzen `:memory:` – Migration muss auch auf frischer DB laufen (ALTER nach CREATE ergibt die Spalte sofort)

**Tests**
- `ApiDatabaseTest`: bestehenden Test `saveAndLoadDirectEndpoint` um Assertion `assertNull(loaded.get(0).getResolvedIp())` erweitern (kein IP gesetzt)
- Neuer Test `saveAndLoadEndpointWithResolvedIp`: Endpoint mit gesetzter IP speichern und prüfen, dass sie korrekt zurückgelesen wird

---

### Sub-Task 3: `IsEndpointCheckService` – `resolveHost`-Aufruf + Parsing

**Status:** [x] done

**Intent**
Der bestehende IS-RAD-Service `resolveHost` soll analog zu `callPingEndpoint` / `callTcpEndpoint` / `callHttpEndpoint` aufrufbar sein. Der Response enthält `host` und `resolved_ip`.

**Expected Outcomes**
- Neue package-private Methode `callResolveHostEndpoint(String url)` die `GET {buildBaseUrl()}/resolveHost?url={encodedUrl}` aufruft
- Neue package-private Methode `parseResolveHostResponse(String json)` die den `resolved_ip`-Wert aus dem JSON extrahiert und als `String` zurückgibt (`null` bei leerem/fehlendem Wert)

**Todo List**
1. Methode `String callResolveHostEndpoint(String url) throws IOException` hinzufügen – implementiert analog zu `callPingEndpoint`, nutzt `callIsEndpoint()` intern
2. Methode `String parseResolveHostResponse(String json)` hinzufügen – nutzt den vorhandenen `parseJsonStrings()`-Mechanismus, extrahiert Key `"resolved_ip"`, gibt `null` zurück wenn leer oder nicht vorhanden

**Relevant Context**
- [`IsEndpointCheckService.java`](app/src/main/java/com/agwcontrol/IsEndpointCheckService.java) – `callPingEndpoint()` Zeile 175 als Vorlage
- [`IsEndpointCheckConfig.buildBaseUrl()`](app/src/main/java/com/agwcontrol/IsEndpointCheckConfig.java:82) liefert den RAD-Basispfad
- [`services/resolveHost/flow.flow`](services/resolveHost/flow.flow) – IS-Service-Definition: Input `url`, Output `host` + `resolved_ip`
- `encodeQueryParam()` bereits vorhanden (Zeile 268)

**Tests**
In `IsEndpointCheckServiceTest`:
- `parseResolveHostResponse_withIp`: JSON `{"host":"backend.example.com","resolved_ip":"10.0.1.42"}` → `"10.0.1.42"`
- `parseResolveHostResponse_emptyIp`: JSON `{"host":"","resolved_ip":""}` → `null`
- `parseResolveHostResponse_nullOrEmpty`: `null` und `""` → `null`
- `StubIsService` um überschreibbare Methode `callResolveHostEndpoint(String url)` ergänzen (gibt konfigurierbares JSON zurück oder wirft IOException)

---

### Sub-Task 4: `AgwApiService` – DNS-Auflösung nach Server-Abruf

**Status:** [x] done

**Intent**
Nach dem Laden der Endpoints vom AGW-Server (nicht aus DB-Cache) soll für jeden Endpoint mit gültiger URL der IS-Service `resolveHost` aufgerufen werden. DNS-Fehler dürfen das Listing nicht blockieren. Der `IsEndpointCheckService` für den resolveHost-Aufruf bekommt `httpDebugConfig` und `debugStream` übergeben, damit der bestehende `[d]`-Debug-Toggle im interaktiven Menü auch die DNS-Auflösung abdeckt.

**Expected Outcomes**
- Die gecachte Variante von `getNativeEndpoints()` erhält zwei zusätzliche Parameter: `IsEndpointCheckConfig isConfig` (nullable) und `HttpDebugConfig debugConfig` (nullable)
- Wenn `isConfig != null` und der Endpoint eine gültige URL hat, wird `callResolveHostEndpoint(url)` aufgerufen und das Ergebnis via `setResolvedIp()` gesetzt
- Debug-Ausgaben des IS-Aufrufs erscheinen im selben Stream wie alle anderen HTTP-Debug-Ausgaben, wenn der Debug-Modus aktiv ist
- IOException beim IS-Aufruf wird still ignoriert – `resolvedIp` bleibt `null`
- Der `db.saveEndpoints()`-Aufruf erfolgt danach (inkl. IP)
- Aufrufe mit `isConfig = null` (kein IS konfiguriert) überspringen die DNS-Auflösung vollständig

**Todo List**
1. Signatur der öffentlichen `getNativeEndpoints()`-Variante (mit `db`, `cache`, `hint`) um `IsEndpointCheckConfig isConfig` und `HttpDebugConfig debugConfig` als letzte Parameter erweitern (beide nullable)
2. Nach `List<RoutingEndpoint> result = getNativeEndpoints(server, apiId)`: wenn `isConfig != null`, über `result` iterieren, pro Endpoint mit nicht-leerer `resolvedUrl` einen `IsEndpointCheckService(isConfig, debugConfig, debugStream)` instanziieren und `callResolveHostEndpoint` + `parseResolveHostResponse` aufrufen; IOException ignorieren
3. Aufruf in `InteractiveMenu` anpassen: `server.getIsProbeConfig()` und `httpDebugConfig` als neue Argumente übergeben

**Relevant Context**
- [`AgwApiService.java`](app/src/main/java/com/agwcontrol/AgwApiService.java) – gecachte Variante `getNativeEndpoints()` Zeile 271
- [`ServerConfig.getIsProbeConfig()`](app/src/main/java/com/agwcontrol/ServerConfig.java:78) – liefert `IsEndpointCheckConfig` oder `null`
- [`InteractiveMenu.java`](app/src/main/java/com/agwcontrol/InteractiveMenu.java) – Aufrufe von `getNativeEndpoints()` in `runEndpointList()` (Zeile 365) und `runEndpointCheck()` (Zeile 417)
- [`HttpDebugConfig.java`](app/src/main/java/com/agwcontrol/HttpDebugConfig.java) – `isEnabled()` / `toggle()` / `label()` – die Instanz `httpDebugConfig` ist in `InteractiveMenu` als Feld vorhanden (Zeile 23)
- `[d]`-Toggle in `runActionMenu()` (Zeile 165–168) schaltet `httpDebugConfig` bereits für `AgwApiService` und `localEndpointCheckService` um – der neue `IsEndpointCheckService` für DNS soll dieselbe Instanz nutzen
- `IsEndpointCheckService` wird für den Endpoint-Check bereits mit `httpDebugConfig` instanziiert (Zeile 442) – gleicher Mechanismus

**Tests**
In `AgwApiServiceTest`: Stub-basierter Test der gecachten Variante mit `isConfig != null` prüft dass `resolvedIp` nach dem Aufruf gesetzt ist; Test mit `isConfig = null` prüft dass kein IS-Aufruf stattfindet.

---

### Sub-Task 5: `InteractiveMenu` – IP-Ausgabe in `runEndpointList()`

**Status:** [x] done

**Intent**
Der Benutzer soll beim Endpoint-Listing die aufgelöste IP-Adresse direkt sehen.

**Expected Outcomes**
- `runEndpointList()` gibt hinter der URL `  [10.x.x.x]` aus, wenn `ep.getResolvedIp() != null`
- Wenn keine IP vorhanden (IS nicht konfiguriert oder Auflösung fehlgeschlagen), ändert sich die Ausgabe nicht
- `runEndpointCheck()` übergibt den neuen `isConfig`-Parameter an `getNativeEndpoints()`, gibt aber keine eigene IP-Ausgabe aus (IP wird im Listing-Kontext gezeigt)

**Todo List**
1. In `runEndpointList()`: Ausgabezeile um `(ep.getResolvedIp() != null ? "  [" + ep.getResolvedIp() + "]" : "")` ergänzen
2. `getNativeEndpoints()`-Aufrufe in `runEndpointList()` und `runEndpointCheck()` um `server.getIsProbeConfig()` als letzten Argument ergänzen

**Relevant Context**
- [`InteractiveMenu.java`](app/src/main/java/com/agwcontrol/InteractiveMenu.java) – `runEndpointList()` Zeile 357–392, `runEndpointCheck()` Zeile 403
- Ausgabeformat aktuell: `"  " + api.getName() + " " + version + ": " + alias + " → " + url`

**Tests**
- `InteractiveMenuTest`: Bestehende Tests prüfen auf konkrete Ausgabestrings – falls nötig, Assertions lockern oder Stub-Endpoint ohne IP testen (kein Breaking Change wenn IP `null`)

---

### Sub-Task 6: `DbReportService` – Spalte `resolved_ip` im pro-Umgebungs-CSV

**Status:** [x] done

**Intent**
Der pro-Umgebungs-Report soll die aufgelöste IP-Adresse zwischen `endpoint_url` und `server_host` ausgeben.

**Expected Outcomes**
- `HEADER`-Konstante enthält `resolved_ip` zwischen `endpoint_url` und `server_host`
- `buildRow()` gibt `ep.getResolvedIp()` (via `csvField()`) an Position 6 (0-indiziert) aus
- Wenn kein IP vorhanden, erscheint ein leeres Feld (keine strukturelle Änderung)
- Anzahl der Felder pro Zeile steigt von 15 auf 16

**Todo List**
1. `HEADER`-Konstante anpassen: `endpoint_url;resolved_ip;server_host;...`
2. In `buildRow()`: nach `csvField(url)` den Wert `csvField(ep.getResolvedIp())` (mit führendem `;`) einfügen; Signatur muss `RoutingEndpoint ep` statt nur `String url` und `String alias` erhalten – oder `resolvedIp` als zusätzlichen String-Parameter
3. Aufrufe von `buildRow()` in `buildCsv()` anpassen

**Relevant Context**
- [`DbReportService.java`](app/src/main/java/com/agwcontrol/DbReportService.java) – `HEADER` Zeile 22, `buildRow()` Zeile 126, `buildCsv()` Zeile 93
- `buildRow()` hat aktuell Signatur `(ApiInfo api, String alias, String url, CheckRow cr)` – einfachste Erweiterung: zusätzlicher Parameter `String resolvedIp`

**Tests**
In `DbReportServiceTest`:
- Bestehende Tests die Feldanzahl `15` prüfen müssen auf `16` aktualisiert werden
- Bestehende Tests die Spaltenindizes `>= 6` prüfen müssen um 1 verschoben werden
- Neuer Test: Endpoint mit gesetzter IP → `cols[6]` enthält die IP
- Neuer Test: Endpoint ohne IP → `cols[6]` leer

---

## Abhängigkeiten zwischen Sub-Tasks

```
Sub-Task 1 (RoutingEndpoint)
    ├── Sub-Task 2 (ApiDatabase)
    ├── Sub-Task 3 (IsEndpointCheckService)
    └── Sub-Task 4 (AgwApiService)          ← benötigt 1+3
            └── Sub-Task 5 (InteractiveMenu) ← benötigt 4
Sub-Task 6 (DbReportService)                ← benötigt 1+2
```

Sub-Tasks 2, 3 und 6 können nach Sub-Task 1 parallel bearbeitet werden.
Sub-Task 4 setzt Sub-Tasks 1 und 3 voraus.
Sub-Task 5 setzt Sub-Task 4 voraus.
