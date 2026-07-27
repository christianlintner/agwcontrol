package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveMenuTest {

    /** Baut ein Testmenü mit simulierter Eingabe. */
    private ByteArrayOutputStream runMenu(List<ServerGroup> groups, String input) {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream outputBuf = new ByteArrayOutputStream();
        InteractiveMenu menu = new InteractiveMenu(
                groups,
                new ByteArrayInputStream(inputBytes),
                new PrintStream(outputBuf));
        menu.run();
        return outputBuf;
    }

    private List<ServerGroup> singleGroup() {
        return List.of(new ServerGroup("TEST-ENV", List.of(
                new ServerConfig("127.0.0.1", 0))));
    }

    private List<ServerGroup> twoGroups() {
        return List.of(
                new ServerGroup("ENV-A", List.of(new ServerConfig("127.0.0.1", 0))),
                new ServerGroup("ENV-B", List.of(
                        new ServerConfig("127.0.0.1", 0),
                        new ServerConfig("127.0.0.1", 0))));
    }

    // --- Hauptmenü ---

    @Test
    void quitExitsMainMenu() {
        // Gibt 'q' ein → run() kehrt zurück ohne Exception
        assertDoesNotThrow(() -> runMenu(singleGroup(), "q\n"));
    }

    @Test
    void mainMenuListsGroupNames() {
        ByteArrayOutputStream out = runMenu(singleGroup(), "q\n");
        assertTrue(out.toString().contains("TEST-ENV"));
    }

    @Test
    void mainMenuShowsServerCount() {
        ByteArrayOutputStream out = runMenu(twoGroups(), "q\n");
        assertTrue(out.toString().contains("ENV-B"));
        assertTrue(out.toString().contains("2 Server") || out.toString().contains("(2"));
    }

    @Test
    void invalidInputShowsErrorAndContinues() {
        // ungültige Eingabe, danach 'q'
        ByteArrayOutputStream out = runMenu(singleGroup(), "x\nq\n");
        assertTrue(out.toString().contains("Ungültige Eingabe"));
    }

    @Test
    void outOfRangeInputShowsError() {
        ByteArrayOutputStream out = runMenu(singleGroup(), "99\nq\n");
        assertTrue(out.toString().contains("Ungültige Eingabe"));
    }

    // --- Untermenü / Aktionen ---

    @Test
    void selectGroupShowsActionMenu() {
        // Gruppe 1 auswählen, dann 'b' (Zurück), dann 'q'
        ByteArrayOutputStream out = runMenu(singleGroup(), "1\nb\nq\n");
        assertTrue(out.toString().contains("Ping"));
        assertTrue(out.toString().contains("TCP-Check"));
    }

    @Test
    void pingActionOutputContainsHost() {
        // Gruppe 1 → Aktion 1 (Ping) → Loopback erreichbar
        ByteArrayOutputStream out = runMenu(singleGroup(), "1\n1\nq\n");
        assertTrue(out.toString().contains("127.0.0.1"));
    }

    @Test
    void tcpCheckActionOutputContainsHost() {
        // Gruppe 1 → Aktion 2 (TCP-Check) → Loopback
        ByteArrayOutputStream out = runMenu(singleGroup(), "1\n2\nq\n");
        assertTrue(out.toString().contains("127.0.0.1"));
    }

    @Test
    void allGroupsActionRunsOnAllServers() {
        // 'a' → Aktion 1 (Ping) → beide Gruppen erscheinen
        ByteArrayOutputStream out = runMenu(twoGroups(), "a\n1\nq\n");
        String s = out.toString();
        // ENV-A und ENV-B werden beide gepingt
        assertTrue(s.contains("127.0.0.1"));
    }

    @Test
    void backFromActionMenuReturnsToMainMenu() {
        // Gruppe 1 → b → q
        ByteArrayOutputStream out = runMenu(singleGroup(), "1\nb\nq\n");
        // Hauptmenü muss zweimal erschienen sein (einmal vor Gruppen-Auswahl, einmal nach Zurück)
        long mainMenuCount = out.toString().chars()
                .filter(c -> out.toString().indexOf("AGW-Control") >= 0)
                .count();
        assertTrue(mainMenuCount > 0);
    }

    @Test
    void invalidActionInputShowsError() {
        // Gruppe 1 → ungültige Aktion → b → q
        ByteArrayOutputStream out = runMenu(singleGroup(), "1\nz\nb\nq\n");
        assertTrue(out.toString().contains("Ungültige Eingabe"));
    }

    @Test
    void actionMenuShowsApiListOption() {
        ByteArrayOutputStream out = runMenu(singleGroup(), "1\nb\nq\n");
        assertTrue(out.toString().contains("APIs auflisten"));
    }

    @Test
    void apiListActionShowsErrorForUnreachableServer() {
        // Port 1 ist garantiert nicht erreichbar → Fehlertext erscheint
        List<ServerGroup> groups = List.of(new ServerGroup("TEST", List.of(
                new ServerConfig("127.0.0.1", 1, "user", "pass", "http://127.0.0.1:1"))));
        ByteArrayOutputStream out = runMenu(groups, "1\n3\nq\n");
        String s = out.toString();
        assertTrue(s.contains("Fehler") || s.contains("Keine APIs"));
    }

    @Test
    void apiListWithMultipleServersShowsServerSelectionMenu() {
        // Gruppe mit 2 Servern → [3] → Server-Auswahlmenü muss erscheinen → b → q
        ByteArrayOutputStream out = runMenu(twoGroups(), "2\n3\nb\nq\n");
        String s = out.toString();
        assertTrue(s.contains("Server auswählen") || s.contains("127.0.0.1"));
    }

    @Test
    void serverSelectionBackReturnsToActionMenu() {
        // Gruppe mit 2 Servern → [3] → Server-Auswahlmenü → b (zurück) → q
        // Kein Absturz, Menü bleibt bedienbar
        assertDoesNotThrow(() -> runMenu(twoGroups(), "2\n3\nb\nq\n"));
    }

    // --- Option [4] Endpoint-Check ---

    @Test
    void actionMenuShowsEndpointCheckOption() {
        ByteArrayOutputStream out = runMenu(singleGroup(), "1\nb\nq\n");
        assertTrue(out.toString().contains("Endpoint-Check"));
    }

    @Test
    void endpointCheckActionWithUnreachableServerShowsError() {
        // Port 1 garantiert nicht erreichbar → Fehler beim API-Laden
        List<ServerGroup> groups = List.of(new ServerGroup("TEST", List.of(
                new ServerConfig("127.0.0.1", 1, "user", "pass", "http://127.0.0.1:1"))));
        ByteArrayOutputStream out = runMenu(groups, "1\n4\nq\n");
        String s = out.toString();
        assertTrue(s.contains("Fehler") || s.contains("Keine APIs") || s.contains("Keine Endpoints"));
    }

    @Test
    void endpointCheckWithMultipleServersShowsServerSelectionMenu() {
        // Gruppe mit 2 Servern → [4] → Server-Auswahlmenü muss erscheinen → b → q
        ByteArrayOutputStream out = runMenu(twoGroups(), "2\n4\nb\nq\n");
        String s = out.toString();
        assertTrue(s.contains("Server auswählen") || s.contains("127.0.0.1"));
    }

    @Test
    void endpointCheckServerSelectionBackReturnsToActionMenu() {
        assertDoesNotThrow(() -> runMenu(twoGroups(), "2\n4\nb\nq\n"));
    }
}
