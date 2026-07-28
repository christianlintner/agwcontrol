# Plan: Endpunkt auflisten und Endpoint-Check trennen (Issue #13)

## Übersicht

Im Aktionsmenü von [`InteractiveMenu`](app/src/main/java/com/agwcontrol/InteractiveMenu.java) soll der bisherige Menüpunkt **[4] Endpoint-Check** in zwei getrennte Funktionen aufgeteilt werden:

- **[4] Endpoints auflisten** — lädt und zeigt die aufgelösten Backend-URLs einer oder mehrerer APIs an, **ohne** Checks auszuführen.
- **[5] Endpoint-Check** — übernimmt das bisherige Verhalten von [4] und führt für die bekannten Backend-URLs weiterhin Ping-, TCP- und HTTP-Checks durch.

**Branch:** `feature/issue-13`

**Betroffene Dateien:**
- [`InteractiveMenu.java`](app/src/main/java/com/agwcontrol/InteractiveMenu.java)
- [`InteractiveMenuTest.java`](app/src/test/java/com/agwcontrol/InteractiveMenuTest.java)

---

## Aktueller Stand

In [`InteractiveMenu.runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:103) sind aktuell diese Aktionen vorhanden:

- `[1] Ping`
- `[2] TCP-Check`
- `[3] APIs auflisten`
- `[4] Endpoint-Check`

Die bestehende Methode [`InteractiveMenu.runEndpointCheck()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:304) lädt pro gewählter API die nativen Endpoints über [`AgwApiService.getNativeEndpoints()`](app/src/main/java/com/agwcontrol/AgwApiService.java), löst Alias-Namen auf und führt anschließend Checks aus. Genau diese Kopplung soll getrennt werden.

Die API-Auswahl wird bereits durch [`InteractiveMenu.selectApis()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:255) erledigt und kann für beide Menüpunkte wiederverwendet werden.

---

## Soll-Verhalten

### Menüstruktur

Im Aktionsmenü soll künftig stehen:

```text
[1] Ping
[2] TCP-Check
[3] APIs auflisten
[4] Endpoints auflisten
[5] Endpoint-Check
```

### [4] Endpoints auflisten

Ablauf:
1. Server auswählen (wie bisher bei [4], falls mehrere Server vorhanden sind).
2. APIs auswählen über [`selectApis()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:255).
3. Für jede gewählte API nativen Endpoint über [`AgwApiService.getNativeEndpoints()`](app/src/main/java/com/agwcontrol/AgwApiService.java) laden.
4. Pro Endpoint die aufgelöste Backend-URL ausgeben:
   - direkte URL aus `nativeEndpoint[].uri`, oder
   - Alias-Auflösung `aliasName -> resolvedUrl`.
5. **Keine** Checks ausführen und **nicht** [`EndpointCheckResultFormatter`](app/src/main/java/com/agwcontrol/EndpointCheckResultFormatter.java) verwenden.

### [5] Endpoint-Check

Ablauf bleibt wie bisher:
1. Server auswählen.
2. APIs auswählen.
3. Native Endpoints laden.
4. Für jede aufgelöste Backend-URL Ping/TCP/HTTP prüfen.
5. Ergebnis über [`EndpointCheckResultFormatter.format()`](app/src/main/java/com/agwcontrol/EndpointCheckResultFormatter.java:7) ausgeben.

---

## Sub-Tasks

### Sub-Task 1 — Aktionsmenü in `InteractiveMenu` erweitern

**Intent:**  
Das Menü so anpassen, dass [4] und [5] getrennt sichtbar und auswählbar sind.

**Expected Outcomes:**  
- [`InteractiveMenu.runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:103) zeigt zusätzlich **[4] Endpoints auflisten**.
- Der bisherige **[4] Endpoint-Check** wird zu **[5] Endpoint-Check**.
- Die Ungültig-Eingabe-Meldung akzeptiert `[5]` ebenfalls.

**Todo List:**
1. Menüausgabe in [`runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:125) um `[4] Endpoints auflisten` ergänzen.
2. Vorhandene Auswahl für Endpoint-Check von `"4"` auf `"5"` verschieben.
3. Fehlermeldung für ungültige Eingaben von `[1], [2], [3], [4], [c], [b] oder [q]` auf `[1], [2], [3], [4], [5], [c], [b] oder [q]` erweitern.

---

### Sub-Task 2 — Methode `runEndpointList(...)` extrahieren

**Intent:**  
Die reine Auflistung der Backend-URLs ohne Checks als eigene Methode aus [`runEndpointCheck()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:304) herauslösen.

**Expected Outcomes:**  
- Neue Methode `runEndpointList(ServerConfig server, ApiSelection sel, String environment)` in [`InteractiveMenu.java`](app/src/main/java/com/agwcontrol/InteractiveMenu.java).
- Die Methode lädt für jede gewählte API die Endpoints mit [`AgwApiService.getNativeEndpoints()`](app/src/main/java/com/agwcontrol/AgwApiService.java).
- Pro gefundenem Endpoint wird die aufgelöste Backend-URL ausgegeben.
- Bei Alias-Endpunkten ist die Ausgabe nachvollziehbar, z. B. `alias -> resolvedUrl`.
- Bei fehlender Auflösung erscheint der bestehende Hinweis analog zum aktuellen Verhalten (`Alias '...' konnte nicht aufgelöst werden.`).
- Wenn keine Endpoints gefunden werden, erscheint eine kompakte Hinweis-Ausgabe statt eines Check-Reports.

**Todo List:**
1. Aus dem Lade-Teil von [`runEndpointCheck()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:305) die gemeinsame Endpoint-Lade-Logik identifizieren.
2. Neue Methode `runEndpointList(...)` anlegen, die dieselbe API-Auswahl verarbeitet, aber nur Informationen ausgibt.
3. In [`runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:166) die neue Auswahl `"4"` auf `runEndpointList(...)` verdrahten.
4. Die bestehende Methode [`runEndpointCheck()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:304) unverändert im Verhalten lassen; sie hängt künftig an Auswahl `"5"`.

---

### Sub-Task 3 — Tests in `InteractiveMenuTest` anpassen und ergänzen

**Intent:**  
Die neue Menüstruktur und der neue Menüpunkt [4] sollen automatisiert abgesichert werden, ohne unnötig andere Bereiche umzubauen.

**Expected Outcomes:**  
- Bestehende Tests für Endpoint-Check verwenden künftig Auswahl `5` statt `4`.
- Neuer Test prüft, dass im Aktionsmenü **Endpoints auflisten** sichtbar ist.
- Neuer Test für den neuen Menüpunkt [4] in [`InteractiveMenuTest`](app/src/test/java/com/agwcontrol/InteractiveMenuTest.java).

**Todo List:**
1. Vorhandene Endpoint-Check-Tests in [`InteractiveMenuTest.java`](app/src/test/java/com/agwcontrol/InteractiveMenuTest.java) von Eingabe `4` auf `5` umstellen.
2. Test ergänzen, dass das Aktionsmenü den Text `Endpoints auflisten` enthält.
3. Einen neuen Test für [4] hinzufügen, z. B. mit nicht erreichbarem Server: Ausgabe enthält `Fehler`, `Keine APIs` oder einen endpointbezogenen Hinweis, aber keinen Absturz.
4. Optional zusätzlich prüfen, dass [5] weiterhin `Endpoint-Check` im Menü anzeigt.

---

## Ausführungsreihenfolge

```text
Sub-Task 1 → Sub-Task 2 → Sub-Task 3
```

Zuerst wird die Menüverdrahtung angepasst, danach die neue reine Listenfunktion implementiert und zuletzt die Tests auf die neue Nummerierung und das neue Verhalten aktualisiert.
