# Issue #27 — Endpoint-Check anhand einer API-Liste filtern

## Ziel & Überblick

Beim Endpoint-Check (`[5]` im interaktiven Menü) soll es möglich sein, den Check auf eine explizite Teilmenge von APIs einzuschränken.

### Design-Entscheidungen

| Frage | Entscheidung |
|---|---|
| CLI vs. Interaktiv | Ist `--api-list-file` (oder `--api-filter`) gesetzt, entfällt die interaktive Filter-Eingabe vollständig. |
| Primärer CLI-Parameter | `--api-list-file <datei>` (eine API pro Zeile). Das ist der Hauptweg (99 % der Fälle). |
| Sekundärer CLI-Parameter | `--api-filter api1,api2` als Komfort-Option (z. B. für schnelle Tests). |
| Case-Sensitivity | Case-insensitiver Vergleich (Lowercase-Normalisierung). |
| Kein Filter | Bisheriges Verhalten bleibt vollständig erhalten (alle APIs werden geprüft). |

### Ablauf mit Filter

```
Programmstart mit --api-list-file apis.txt
  └─ App.java liest Datei → Set<String> (lowercase)
       └─ InteractiveMenu erhält apiFilterNames
            └─ [5] Endpoint-Check gewählt
                 └─ selectApis(): Lädt alle APIs vom Server
                      └─ Filter anwenden (kein interaktiver Prompt)
                           └─ Gefilterte Liste → runEndpointCheck()
```

---

## Sub-Task 1 — CLI-Parameter in `App.java` parsen und weiterreichen

**Status:** `[x] done`

### Intent
`App.java` parst bereits `--kdbx` und `--kdbx-password`. Neu kommen `--api-list-file <datei>` und `--api-filter <namen>` hinzu. Das Ergebnis ist ein `Set<String>` (lowercase), das an `InteractiveMenu` übergeben wird.

### Expected Outcomes
- `--api-list-file apis.txt` liest Zeilen aus der Datei (eine API pro Zeile, Leerzeilen und `#`-Kommentare überspringen) und baut daraus ein `Set<String>` (lowercase).
- `--api-filter api1,api2` splittet komma-getrennt und fügt die Tokens (lowercase) zum Set hinzu.
- Werden beide Parameter angegeben, werden die Mengen vereinigt.
- Ist kein Parameter angegeben, ist das Set leer → kein Filter.
- Fehler beim Lesen der Datei werden mit verständlicher Fehlermeldung auf `stderr` ausgegeben und beenden das Programm.

### Todo List
1. In der Parsing-Schleife in `App.main()` zwei neue Variablen `apiFilter` (String) und `apiListFile` (String) ergänzen.
2. Hilfsmethode `static Set<String> buildApiFilterSet(String apiFilter, String apiListFile)` in `App.java` erstellen:
   - Komma-Split und Lowercase für `apiFilter`-Token.
   - `Files.readAllLines()` für `apiListFile`, Zeilen trimmen, Leerzeilen und Zeilen mit `#` überspringen, Lowercase.
   - Beide Sets vereinigen und zurückgeben.
3. `InteractiveMenu`-Konstruktoren um `Set<String> apiFilterNames` erweitern (bestehende Konstruktoren delegieren mit `Set.of()` für Rückwärtskompatibilität).
4. `printUsage()` um die neuen Parameter ergänzen.

### Relevant Context
- [`App.java:11-38`](app/src/main/java/com/agwcontrol/App.java) — `main()` Parsing-Schleife
- [`App.java:73-76`](app/src/main/java/com/agwcontrol/App.java) — `printUsage()`
- [`InteractiveMenu.java:34-50`](app/src/main/java/com/agwcontrol/InteractiveMenu.java) — Konstruktoren

---

## Sub-Task 2 — Filterlogik in `InteractiveMenu.selectApis()`

**Status:** `[x] done`

### Intent
`selectApis()` lädt die vollständige API-Liste vom Server. Ist `apiFilterNames` nicht leer, wird die Liste unmittelbar nach dem Laden gefiltert — ohne dass eine interaktive Filter-Eingabe erscheint. Ist `apiFilterNames` leer, verhält sich `selectApis()` genau wie bisher.

### Expected Outcomes
- Ist ein Filter gesetzt: Die API-Liste wird case-insensitiv auf Einträge reduziert, deren `getName()` im Filter-Set enthalten ist.
- Trifft der Filter auf keine API zu: Klare Fehlermeldung, Methode gibt `null` zurück (Check wird abgebrochen).
- Ist kein Filter gesetzt: Bestehendes Verhalten (interaktive Auswahl via `selectApisFromLoadedList()`) bleibt unverändert.
- Filterung ist case-insensitiv (Lowercase-Vergleich auf beiden Seiten).

### Todo List
1. `apiFilterNames`-Feld (`Set<String>`) in `InteractiveMenu` ergänzen, im Konstruktor gesetzt.
2. In `selectApis()` nach dem Laden der API-Liste prüfen, ob `apiFilterNames` nicht leer ist:
   - Wenn ja: `apis` per `stream().filter()` auf passende Namen reduzieren (`.getName().toLowerCase()`).
   - Wenn die gefilterte Liste leer ist: Fehlermeldung ausgeben, `null` zurückgeben.
   - Wenn nicht leer: Direkt `new ApiSelection(filteredApis, false)` zurückgeben (kein `selectApisFromLoadedList()`-Aufruf).
3. Bestehender Pfad (kein Filter) bleibt unberührt.

### Relevant Context
- [`InteractiveMenu.java:266-284`](app/src/main/java/com/agwcontrol/InteractiveMenu.java) — `selectApis()`
- [`InteractiveMenu.java:256-264`](app/src/main/java/com/agwcontrol/InteractiveMenu.java) — `ApiSelection`-Klasse
- [`InteractiveMenu.java:191-199`](app/src/main/java/com/agwcontrol/InteractiveMenu.java) — Aufruf in `runActionMenu()`

---

## Sub-Task 3 — Tests

**Status:** `[x] done`

### Intent
Sicherstellen, dass der Filtercode korrekt funktioniert und bestehende Tests nicht bricht.

### Expected Outcomes
- Unit-Tests für `App.buildApiFilterSet()`:
  - `--api-list-file` mit mehrzeiliger Datei (inkl. Leerzeilen und `#`-Kommentaren).
  - `--api-filter` mit komma-getrennten Namen.
  - Kombination beider Parameter.
  - Kein Parameter → leeres Set.
- Unit-Tests für `InteractiveMenu.selectApis()` (Mock-Scanner):
  - Filter trifft → gefilterte `ApiSelection` wird zurückgegeben.
  - Filter trifft nicht → `null` zurückgegeben + Fehlermeldung.
  - Kein Filter → Aufruf von `selectApisFromLoadedList()` wie bisher.
- `./gradlew test` läuft ohne neue Fehler durch.

### Todo List
1. Testfälle für `App.buildApiFilterSet()` in `AppTest` (oder neuer Klasse) anlegen.
2. Testfälle für die Filterlogik in `InteractiveMenuTest` ergänzen.
3. `./gradlew test` ausführen und alle Tests auf Grün bringen.

### Relevant Context
- [`app/src/test/java/com/agwcontrol/`](app/src/test/java/com/agwcontrol/) — bestehende Testklassen

---

## Akzeptanzkriterien (aus Issue #27)

- [x] `--api-list-file` und `--api-filter` werden von `App.java` geparst und an `InteractiveMenu` weitergereicht.
- [x] Ist ein Filter gesetzt, werden beim Endpoint-Check nur die angegebenen APIs geprüft.
- [x] Filtervergleich ist case-insensitiv.
- [x] Ist ein Filter gesetzt, entfällt die interaktive Filter-Eingabe.
- [x] Ist kein Filter gesetzt, bleibt das bisherige Verhalten vollständig erhalten.
- [x] Bestehende Tests laufen weiterhin erfolgreich durch.
- [x] Neue Tests decken den Filterfall (Treffer, kein Treffer, kein Filter) ab.
