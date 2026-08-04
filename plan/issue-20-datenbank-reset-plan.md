# Plan: Issue #20 – Datenbank-Reset über das interaktive Menü

## Übersicht

Ziel ist es, eine neue Funktion zum Zurücksetzen der lokalen SQLite-Datenbank
(`agwcontrol.db`) bereitzustellen. Der Reset ist über einen neuen Menüpunkt
`[x]  Datenbank zurücksetzen` im Hauptmenü erreichbar, wird durch eine
Bestätigungsabfrage gesichert und leert alle drei Tabellen (`apis`, `endpoints`,
`endpoint_check_results`).

---

## Sub-Tasks

### 1. `ApiDatabase` – Methode `resetAll()` ergänzen

**Intent**  
Kapselt die Datenbanklogik zum Leeren aller Tabellen in einer einzigen,
gut testbaren Methode.

**Expected Outcomes**
- `ApiDatabase` besitzt eine public-Methode `resetAll() throws SQLException`
- Die Methode löscht alle Zeilen aus `endpoint_check_results`, `endpoints` und `apis` (in dieser Reihenfolge, damit FK-Abhängigkeiten kein Problem werden)
- Die drei DELETE-Statements laufen in einer einzigen Transaktion

**Todo List**
- [ ] Methode `resetAll()` in `ApiDatabase.java` unter dem Abschnitt *Hilfsmethoden* einfügen
- [ ] Transaktion mit `setAutoCommit(false)` / `commit()` / `finally setAutoCommit(true)` absichern

**Relevant Context**
- Datei: `app/src/main/java/com/agwcontrol/ApiDatabase.java`
- Muster: `saveEndpoints()` verwendet dieselbe Transaktionslogik – als Vorlage verwenden

**Status** `[ ] pending`

---

### 2. `ApiDatabaseTest` – Tests für `resetAll()`

**Intent**  
Sicherstellen, dass `resetAll()` korrekt funktioniert und keine unerwarteten
Fehler wirft.

**Expected Outcomes**
- Test `resetAllClearsAllTables`: befüllt alle 3 Tabellen, ruft `resetAll()` auf,
  prüft dass `loadApis()`, `loadEndpoints()` und `loadCheckResults()` danach leer sind
- Test `resetAllOnEmptyDbDoesNotThrow`: leere DB, `resetAll()` darf keine Exception werfen

**Todo List**
- [ ] Test `resetAllClearsAllTables()` in `ApiDatabaseTest.java` hinzufügen
- [ ] Test `resetAllOnEmptyDbDoesNotThrow()` in `ApiDatabaseTest.java` hinzufügen

**Relevant Context**
- Datei: `app/src/test/java/com/agwcontrol/ApiDatabaseTest.java`
- In-Memory-DB wird via `new ApiDatabase(":memory:")` erzeugt – Muster bereits vorhanden

**Status** `[ ] pending`

---

### 3. `InteractiveMenu` – Menüpunkt, Input-Handling und Methode

**Intent**  
Den Nutzer über das interaktive Menü in die Lage versetzen, die Datenbank
zurückzusetzen – mit Bestätigungsabfrage als Sicherheitsnetz.

**Expected Outcomes**
- Hauptmenü zeigt neuen Eintrag `[x]  Datenbank zurücksetzen`
- Eingabe `x` (case-insensitive) in `run()` ruft `runDatabaseReset()` auf
- `runDatabaseReset()` fragt `Datenbank wirklich zurücksetzen? [j/N]:` und
  ruft nur bei Eingabe `j` die Methode `apiDatabase.resetAll()` auf
- Bei Erfolg: Ausgabe `Datenbank wurde erfolgreich zurückgesetzt.`
- Bei Abbruch (alles außer `j`): Ausgabe `Abgebrochen.`
- Fehlermeldung für ungültige Hauptmenüeingabe wird um `[x]` erweitert

**Todo List**
- [ ] In `printMainMenu()`: neuen Eintrag `[x]  Datenbank zurücksetzen` nach `[r]` einfügen
- [ ] In `run()`: `if ("x".equalsIgnoreCase(input))` Branch ergänzen
- [ ] Fehlermeldung für ungültige Eingabe in `run()` aktualisieren (enthält `[x]`)
- [ ] Methode `runDatabaseReset()` implementieren

**Relevant Context**
- Datei: `app/src/main/java/com/agwcontrol/InteractiveMenu.java`
- Methode `runReport()` (Zeile 445) als Muster für ähnlichen Hauptmenü-Flow
- Methode `run()` (Zeile 58): Input-Handling Muster bereits etabliert

**Status** `[ ] pending`

---

### 4. `InteractiveMenuTest` – Tests für den DB-Reset-Menüpunkt

**Intent**  
Sicherstellen, dass der neue Menüpunkt angezeigt wird und die
Bestätigungslogik korrekt funktioniert.

**Expected Outcomes**
- Test `mainMenuShowsDatabaseResetOption`: Ausgabe enthält `Datenbank zurücksetzen`
- Test `databaseResetWithConfirmationJa`: Eingabe `x\nj\n` → Ausgabe enthält Erfolgsmeldung
- Test `databaseResetWithoutConfirmation`: Eingabe `x\nN\n` → Ausgabe enthält `Abgebrochen`

**Todo List**
- [ ] Test `mainMenuShowsDatabaseResetOption()` hinzufügen
- [ ] Test `databaseResetWithConfirmationJa()` hinzufügen
- [ ] Test `databaseResetWithoutConfirmation()` hinzufügen

**Relevant Context**
- Datei: `app/src/test/java/com/agwcontrol/InteractiveMenuTest.java`
- Hilfsmethode `runMenu(groups, input)` bereits vorhanden – wiederverwenden

**Status** `[ ] pending`
