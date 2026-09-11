# Plan: Cluster- und Konzernhub-URLs für Ping und TCP

## Top-Level Overview

Die vier neuen KDBX-Variablen `CLUSTER-EXTERN-URL`, `CLUSTER-EXTERN-CERT-URL`, `KONZERNHUB-CERT-URL` und `KONZERNHUB-EXTERN-CERT-URL` werden als zusätzliche, optionale Endpunkte in die Serverkonfiguration aufgenommen und sowohl bei Ping- als auch bei TCP-Prüfungen berücksichtigt. Fehlt eine Variable, laufen die Checks weiter und geben jeweils eine eigene Zeile `<Label> | nicht konfiguriert` aus.

Die bestehenden Prüfungen und deren Verhalten bleiben unverändert.

## Sub-Task 1: Konfiguration erweitern

- **Intent** — Die vier neuen optionalen KDBX-Custom-Fields müssen eingelesen und im Servermodell verfügbar gemacht werden.
- **Expected Outcomes** — `CLUSTER-EXTERN-URL`, `CLUSTER-EXTERN-CERT-URL`, `KONZERNHUB-CERT-URL` und `KONZERNHUB-EXTERN-CERT-URL` werden aus der KDBX geladen; fehlende Felder führen zu keinem Abbruch und liefern definierte fehlende Werte.
- **Todo List**
  1. Alle vier Felder in `KeePassConfigLoader` auslesen.
  2. Die Felder in `ServerConfig` speichern und über Getter bereitstellen.
  3. Loader-Tests für vorhandene und fehlende Felder ergänzen.
- **Relevant Context** — `app/src/main/java/com/agwcontrol/KeePassConfigLoader.java`, `app/src/main/java/com/agwcontrol/ServerConfig.java`, `app/src/test/java/com/agwcontrol/KeePassConfigLoaderTest.java`
- **Status** — [x] done

## Sub-Task 2: Ping-Prüfung erweitern

- **Intent** — Die vier neuen Endpunkte sollen zusätzlich zu den bestehenden Ping-Zielen geprüft werden.
- **Expected Outcomes** — Vorhandene URLs werden mit den Labels `CLUSTER-EXTERN`, `CLUSTER-EXTERN-CERT`, `KONZERNHUB-CERT` und `KONZERNHUB-EXTERN-CERT` geprüft. Fehlende URLs erzeugen jeweils eine eigene Zeile `<Label> | nicht konfiguriert`, ohne Abbruch.
- **Todo List**
  1. `PingService.pingAll` um alle vier URL-Prüfungen erweitern.
  2. Für jede fehlende URL ein Ergebnis mit dem jeweiligen Label und Status `nicht konfiguriert` vorsehen.
  3. Ping-Tests für vorhandene und fehlende URLs ergänzen.
- **Relevant Context** — `app/src/main/java/com/agwcontrol/PingService.java`, `app/src/main/java/com/agwcontrol/PingResult.java`, `app/src/main/java/com/agwcontrol/PingResultFormatter.java`, `app/src/test/java/com/agwcontrol/PingServiceTest.java`, `app/src/test/java/com/agwcontrol/PingResultFormatterTest.java`
- **Status** — [x] done

## Sub-Task 3: TCP-Prüfung erweitern

- **Intent** — Die vier neuen Endpunkte sollen zusätzlich zu den bestehenden TCP-Zielen geprüft werden.
- **Expected Outcomes** — Vorhandene URLs werden mit dem URL-Port und den Labels `CLUSTER-EXTERN`, `CLUSTER-EXTERN-CERT`, `KONZERNHUB-CERT` und `KONZERNHUB-EXTERN-CERT` geprüft. Fehlende URLs erzeugen jeweils eine eigene Zeile `<Label> | nicht konfiguriert`, ohne Abbruch.
- **Todo List**
  1. `TcpCheckService.checkAll` um alle vier URL-Prüfungen erweitern.
  2. Für jede fehlende URL ein Ergebnis mit dem jeweiligen Label und Status `nicht konfiguriert` vorsehen.
  3. TCP-Tests für vorhandene, fehlende und URL-Port-Fälle ergänzen.
- **Relevant Context** — `app/src/main/java/com/agwcontrol/TcpCheckService.java`, `app/src/main/java/com/agwcontrol/TcpCheckResult.java`, `app/src/test/java/com/agwcontrol/TcpCheckServiceTest.java`
- **Status** — [x] done

## Reihenfolge der zusätzlichen Prüfungen

Die bestehende Reihenfolge bleibt erhalten. Danach werden die vier neuen Ziele in dieser Reihenfolge ausgegeben:

```text
CLUSTER-EXTERN
CLUSTER-EXTERN-CERT
KONZERNHUB-CERT
KONZERNHUB-EXTERN-CERT
```

## Sub-Task 4: Ausgabe und Regression validieren

- **Intent** — Sicherstellen, dass der neue Status in den vorhandenen Ausgaben korrekt sichtbar ist und keine bestehenden Prüfungen beschädigt werden.
- **Expected Outcomes** — Ping- und TCP-Ausgaben enthalten die neue Zeile; bestehende Tests und der Build laufen erfolgreich.
- **Todo List**
  1. Die Formatter- und Ergebnisdarstellung auf `nicht konfiguriert` prüfen und nur minimal anpassen, falls erforderlich.
  2. Relevante Tests ausführen.
  3. `./gradlew clean build` ausführen.
- **Relevant Context** — `app/src/main/java/com/agwcontrol/PingResult.java`, `app/src/main/java/com/agwcontrol/TcpCheckResult.java`, `app/src/main/java/com/agwcontrol/PingResultFormatter.java`, `app/src/test`
- **Status** — [x] done
