# Plan: Endpoint-Alias korrekt verwenden, API-Menüfluss verbessern und HTTP-Debug-Mode ergänzen

## Übersicht

Dieser Task bündelt drei eng zusammenhängende Anpassungen im interaktiven API-Bereich:

- Der Bug bei der Endpoint-Ermittlung soll behoben werden, sodass ein definierter Alias nicht mehr als `dummy.dummy` angezeigt oder aus dem Cache wiedergegeben wird.
- Beim Menüpunkt [`InteractiveMenu.runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:103) für das Auflisten von Endpoints soll der Nutzer nach einer Anzeige im API-Auswahlfluss bleiben, statt direkt ins Hauptmenü zurückzuspringen. Die API-Liste soll dabei bei großen Umgebungen nicht nach jeder Auswahl erneut vollständig ausgegeben werden.
- Es soll ein schaltbarer HTTP-Debug-Mode eingeführt werden, der Requests, Response-Status und optional Response-Body für IS-Aufrufe über die bestehende Ausgabe im interaktiven Menü sichtbar macht.

Der Schwerpunkt liegt auf minimalen, gezielten Änderungen in den bereits vorhandenen Services und im Menüfluss, ohne zusätzliche allgemeine Logging-Abstraktionen einzuführen.

## Sub-Tasks

### [x] Sub-Task 1 — Alias-Bug in Endpoint-Ermittlung und Cache-Pfad absichern

**Intent**  
Sicherstellen, dass Alias-Endpunkte sowohl beim direkten Laden vom IS als auch beim erneuten Lesen aus der Datenbank konsistent als Alias behandelt werden, damit `dummy.dummy` nicht als fachliches Ergebnis sichtbar bleibt.

**Expected Outcomes**  
- Alias-Endpunkte werden in [`AgwApiService.getNativeEndpoints()`](app/src/main/java/com/agwcontrol/AgwApiService.java:33) weiterhin über ihren Alias-Namen aufgelöst.
- Der DB-Cache in [`ApiDatabase.loadEndpoints()`](app/src/main/java/com/agwcontrol/ApiDatabase.java:180) gibt Alias- und Direkt-Endpoints konsistent zurück.
- Die Ausgabe in [`InteractiveMenu.runEndpointList()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:311) zeigt bei vorhandenen Aliasen nicht mehr `dummy.dummy`, sondern Aliasname und aufgelöste URL oder den bestehenden Hinweis bei nicht auflösbaren Aliasen.
- Vorhandene oder neue Tests decken den Alias-Fall inklusive Cache-Pfad ab.

**Todo List**
1. Den Datenfluss von [`AgwApiService.parseNativeEndpoints()`](app/src/main/java/com/agwcontrol/AgwApiService.java:251) über [`AgwApiService.getNativeEndpoints()`](app/src/main/java/com/agwcontrol/AgwApiService.java:33) bis [`ApiDatabase.saveEndpoints()`](app/src/main/java/com/agwcontrol/ApiDatabase.java:143) und [`ApiDatabase.loadEndpoints()`](app/src/main/java/com/agwcontrol/ApiDatabase.java:180) auf Alias-Konsistenz prüfen.
2. Die minimale Korrektur an der Stelle einplanen, an der Alias-Information beim erneuten Lesen verloren geht oder falsch interpretiert wird.
3. Relevante Tests in [`AgwApiServiceTest`](app/src/test/java/com/agwcontrol/AgwApiServiceTest.java) und falls nötig in Tests für [`ApiDatabase`](app/src/main/java/com/agwcontrol/ApiDatabase.java:19) ergänzen oder anpassen.

**Relevant Context**  
- [`AgwApiService.getNativeEndpoints()`](app/src/main/java/com/agwcontrol/AgwApiService.java:33)
- [`AgwApiService.parseNativeEndpoints()`](app/src/main/java/com/agwcontrol/AgwApiService.java:251)
- [`ApiDatabase.saveEndpoints()`](app/src/main/java/com/agwcontrol/ApiDatabase.java:143)
- [`ApiDatabase.loadEndpoints()`](app/src/main/java/com/agwcontrol/ApiDatabase.java:180)
- [`RoutingEndpoint`](app/src/main/java/com/agwcontrol/RoutingEndpoint.java)

### [x] Sub-Task 2 — Endpoint-Liste im API-Auswahlfluss halten

**Intent**
Den Menüfluss für das reine Auflisten von Endpoints so anpassen, dass nach einer Anzeige direkt die nächste API gewählt werden kann, ohne zurück ins Hauptmenü zu springen und ohne die vollständige API-Liste jedes Mal erneut auszugeben.

**Expected Outcomes**
- Auswahl `4` in [`InteractiveMenu.runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:103) beendet den API-Arbeitsfluss nicht sofort.
- Die API-Liste in [`InteractiveMenu.selectApis()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:266) wird für den Listenfall nur einmal vollständig angezeigt und danach nicht bei jeder weiteren Auswahl erneut gedruckt.
- Nach [`InteractiveMenu.runEndpointList()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:311) kann der Nutzer direkt eine weitere API-Nummer eingeben, alle APIs wählen, die Liste bei Bedarf erneut anzeigen oder mit `b` zurückgehen.
- Das Verhalten von Auswahl `5` für den eigentlichen Endpoint-Check bleibt unverändert.
- Tests decken den veränderten Rücksprung und die reduzierte Wiederholung der API-Liste für Auswahl `4` ab.

**Todo List**
1. Den bestehenden Rücksprung in [`InteractiveMenu.runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:167) für Auswahl `4` isolieren.
2. Die API-Auswahl in [`InteractiveMenu.selectApis()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:266) so aufteilen, dass die Liste initial vollständig angezeigt wird, danach aber weitere Eingaben ohne kompletten Re-Print möglich sind.
3. Für den Listenfall einen kompakten Folge-Prompt einplanen, der direkte API-Nummern, `a`, `b` und optional ein erneutes Anzeigen der Liste unterstützt.
4. [`InteractiveMenu.runEndpointList()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:311) in diesen Auswahlfluss einhängen, ohne das Verhalten von Auswahl `5` zu verändern.
5. Tests in [`InteractiveMenuTest`](app/src/test/java/com/agwcontrol/InteractiveMenuTest.java) um das Verbleiben im API-Kontext und das Ausbleiben des vollständigen Re-Prints ergänzen oder anpassen.

**Relevant Context**  
- [`InteractiveMenu.runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:103)
- [`InteractiveMenu.selectApis()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:266)
- [`InteractiveMenu.runEndpointList()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:311)
- [`InteractiveMenu.runEndpointCheck()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:352)
- [`InteractiveMenuTest`](app/src/test/java/com/agwcontrol/InteractiveMenuTest.java)

### [x] Sub-Task 3 — Schaltbaren HTTP-Debug-Mode für IS-Aufrufe ergänzen

**Intent**  
Einen einfachen, sitzungsweiten Debug-Mode bereitstellen, der sich im interaktiven Menü ein- und ausschalten lässt und die relevanten HTTP-Details der IS-Aufrufe sichtbar macht.

**Expected Outcomes**  
- Im Aktionsmenü von [`InteractiveMenu.runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:103) gibt es einen zusätzlichen Toggle für HTTP-Debug.
- HTTP-Aufrufe aus [`AgwApiService`](app/src/main/java/com/agwcontrol/AgwApiService.java) loggen im Debug-Mode Request-Ziel, Response-Status und optional Response-Body.
- HTTP-Aufrufe gegen Backend-Endpoints in [`EndpointCheckService.doRequest()`](app/src/main/java/com/agwcontrol/EndpointCheckService.java:108) bleiben unverändert, sofern der Scope auf IS-Aufrufe begrenzt bleibt.
- Die Debug-Ausgabe verwendet die bestehende interaktive Ausgabe statt einer neuen Logging-Infrastruktur.

**Todo List**
1. Den Scope des Debug-Modes auf die IS-Kommunikation in [`AgwApiService`](app/src/main/java/com/agwcontrol/AgwApiService.java) festziehen, da dies den beschriebenen Bedarf zu den IS-Aufrufen direkt abdeckt.
2. Eine kleine Konfiguration oder einen einfachen Zustand im Menü einplanen, der während der Session umgeschaltet werden kann und an [`AgwApiService`](app/src/main/java/com/agwcontrol/AgwApiService.java) weitergereicht wird.
3. Die Debug-Ausgabe so strukturieren, dass Request, Status und optional Body lesbar sind, ohne die normale Erfolgsanzeige zu ersetzen.
4. Tests oder gezielte Absicherung für den Menü-Toggle und die debuggesteuerte Ausgabe festlegen.

**Relevant Context**  
- [`InteractiveMenu.runActionMenu()`](app/src/main/java/com/agwcontrol/InteractiveMenu.java:103)
- [`AgwApiService.openConnection()`](app/src/main/java/com/agwcontrol/AgwApiService.java:195)
- [`AgwApiService.readBody()`](app/src/main/java/com/agwcontrol/AgwApiService.java:217)
- [`EndpointCheckService.doRequest()`](app/src/main/java/com/agwcontrol/EndpointCheckService.java:108)
- Muster für Menü-Toggles in [`DbCacheConfig`](app/src/main/java/com/agwcontrol/DbCacheConfig.java)
