# Plan: IS-URL Custom Field für IsEndpointCheckConfig verwenden

## Übersicht

**Problem:** In `KeePassConfigLoader.entriesFromGroup()` wird das KeePass Custom Field `IS-URL`
zwar eingelesen, aber vollständig ignoriert. Die `IsEndpointCheckConfig` wird stattdessen immer
aus Host, Port und Scheme der AGW-Haupt-URL gebaut. Das führt dazu, dass Probe-Requests an den
falschen Host/Port gesendet werden (z. B. Port 443 statt IS-Port 5559).

**Ziel:** Wenn ein Eintrag das Custom Field `IS-URL` enthält, sollen dessen Scheme, Host und Port
für die `IsEndpointCheckConfig` verwendet werden. Fehlt `IS-URL`, bleibt das bisherige Verhalten
als Fallback erhalten.

**Scope:** Nur `KeePassConfigLoader.java` und `KeePassConfigLoaderTest.java`.
Keine Änderungen an `IsEndpointCheckConfig`, `UpdateTestKdbx` oder `test.kdbx` nötig —
die Testdatenbank enthält bereits einen Eintrag mit `IS-URL` Custom Field
(`https://vm40757.linux.oebb.at:443`), wie der Test `parsesIsUrl()` beweist.

---

## Sub-Task 1 — `KeePassConfigLoader` verwende IS-URL für IsEndpointCheckConfig

**Status:** [ ] pending

### Intent
Den Bug beheben: Wenn `isUrl` gesetzt ist, dessen Scheme/Host/Port in die `IsEndpointCheckConfig`
einbauen statt der AGW-URL-Werte. Kommentar aktualisieren.

### Expected Outcomes
- Ist `IS-URL` im KeePass-Eintrag gesetzt, zeigt `isProbeConfig.getHost()`/`getPort()`/`getScheme()`
  auf die Werte aus `IS-URL`.
- Ist `IS-URL` nicht gesetzt, bleibt das bisherige Verhalten (Fallback auf AGW-URL) unverändert.
- Der veraltete Kommentar auf Zeile 81–82 beschreibt das neue Verhalten.

### Todo List
1. In `entriesFromGroup()`: Nach dem Einlesen von `isUrl` prüfen ob es nicht `null` ist.
2. Falls ja: `parseScheme(isUrl)`, `parseHost(isUrl)`, `parsePort(isUrl)` aufrufen und deren
   Ergebnisse für `IsEndpointCheckConfig` verwenden.
3. Falls nein (Fallback): wie bisher `scheme`, `host`, `port` aus der AGW-URL verwenden.
4. Kommentar auf Zeile 81–82 durch einen korrekten Kommentar ersetzen, der das neue
   Fallback-Verhalten beschreibt.

### Relevant Context
- Datei: `app/src/main/java/com/agwcontrol/KeePassConfigLoader.java`, Methode `entriesFromGroup()`, Zeilen 75–86
- Hilfsmethoden `parseScheme()`, `parseHost()`, `parsePort()` sind bereits vorhanden und können
  direkt mit `isUrl` aufgerufen werden.
- `IsEndpointCheckConfig` braucht keinen neuen Konstruktor — der vorhandene 5-Parameter-Konstruktor
  `(scheme, host, port, username, password)` reicht.

---

## Sub-Task 2 — Tests für neues IS-URL-Verhalten schreiben

**Status:** [ ] pending

### Intent
Den bestehenden Test `isProbeConfigBuiltFromStandardFields()` anpassen und einen neuen Test
hinzufügen, der explizit prüft dass bei gesetztem `IS-URL` Custom Field die Probe-Config
dessen Werte verwendet.

### Expected Outcomes
- Neuer Test `isProbeConfigUsesIsUrlWhenPresent()`: Lädt den Eintrag `vm40757.linux.oebb.at`
  (hat `IS-URL` = `https://vm40757.linux.oebb.at:443`), prüft dass `isProbeConfig` die
  Werte aus `IS-URL` verwendet (Host, Port, Scheme).
- Bestehender Test `isProbeConfigBuiltFromStandardFields()` wird auf einen Eintrag **ohne**
  `IS-URL` umgestellt (z. B. DN2020-DEV), damit er den Fallback-Pfad testet.
- Test `allEntriesHaveIsProbeConfig()` bleibt unverändert grün.

### Todo List
1. In `KeePassConfigLoaderTest`: Neuen Test `isProbeConfigUsesIsUrlWhenPresent()` hinzufügen.
   - Lädt Eintrag `vm40757.linux.oebb.at` (hat `IS-URL` Custom Field).
   - Prüft `probe.getHost()`, `probe.getPort()`, `probe.getScheme()` gegen die Werte aus IS-URL.
   - Prüft dass Credentials (`agwuser` / `agwpassword1`) unverändert aus Entry-Standard-Feldern kommen.
2. Bestehenden Test `isProbeConfigBuiltFromStandardFields()` auf einen Eintrag ohne `IS-URL`
   umstellen (DN2020-DEV), damit er gezielt den Fallback testet.

### Relevant Context
- Datei: `app/src/test/java/com/agwcontrol/KeePassConfigLoaderTest.java`, ab Zeile 155
- Eintrag `vm40757.linux.oebb.at` hat `IS-URL` = `https://vm40757.linux.oebb.at:443` (Custom Field, belegt durch `parsesIsUrl()`-Test)
- DN2020-DEV-Eintrag hat **kein** `IS-URL` Custom Field → eignet sich für Fallback-Test
- Credentials (`agwuser` / `agwpassword1`) kommen immer aus dem Entry, nicht aus IS-URL
