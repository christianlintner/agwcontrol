package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PingResultFormatterTest {

    private final PingResultFormatter formatter = new PingResultFormatter();

    @Test
    void formatsOkAndUnreachable() {
        List<PingResult> results = List.of(
                new PingResult("agw-server-1.example.com", true, 12),
                new PingResult("agw-server-2.example.com", false, -1)
        );

        String output = formatter.format(results);
        String[] lines = output.split(System.lineSeparator());

        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("agw-server-1.example.com"), "Host in Zeile 1");
        assertTrue(lines[0].contains("OK"), "Status OK in Zeile 1");
        assertTrue(lines[0].contains("12ms"), "Zeit in Zeile 1");
        assertTrue(lines[1].contains("agw-server-2.example.com"), "Host in Zeile 2");
        assertTrue(lines[1].contains("UNREACHABLE"), "Status UNREACHABLE in Zeile 2");
        assertTrue(lines[1].contains("-"), "Kein Zeitwert in Zeile 2");
    }

    @Test
    void hostColumnAligned() {
        List<PingResult> results = List.of(
                new PingResult("short", true, 5),
                new PingResult("much-longer-host.example.com", true, 10)
        );

        String output = formatter.format(results);
        String[] lines = output.split(System.lineSeparator());

        // Beide Zeilen müssen gleich lang sein (Spalten ausgerichtet)
        assertEquals(lines[0].length(), lines[1].length());
    }

    @Test
    void emptyListReturnsEmptyString() {
        assertEquals("", formatter.format(List.of()));
    }

    @Test
    void formatShowsLabelColumnWhenAnyLabelSet() {
        List<PingResult> results = List.of(
                new PingResult("agw-node.example.com",     "AGW",          true,  10),
                new PingResult("is-node.example.com",      "IS",           true,  12),
                new PingResult("cluster.example.com",      "CLUSTER",      false, -1),
                new PingResult("cluster-cert.example.com", "CLUSTER-CERT", false, -1)
        );

        String output = formatter.format(results);
        String[] lines = output.split(System.lineSeparator());

        assertEquals(4, lines.length);
        assertTrue(lines[0].contains("AGW"));
        assertTrue(lines[1].contains("IS"));
        assertTrue(lines[2].contains("CLUSTER"));
        assertTrue(lines[3].contains("CLUSTER-CERT"));
        // Alle Zeilen gleich lang (ausgerichtet)
        assertEquals(lines[0].length(), lines[1].length());
        assertEquals(lines[0].length(), lines[2].length());
        assertEquals(lines[0].length(), lines[3].length());
    }

    @Test
    void formatWithoutLabelsUnchangedBehaviour() {
        List<PingResult> results = List.of(
                new PingResult("host-a.example.com", true,  5),
                new PingResult("host-b.example.com", false, -1)
        );
        String output = formatter.format(results);
        // keine Label-Spalte → kein "AGW" oder ähnliches im Output
        assertFalse(output.contains("AGW"));
        assertFalse(output.contains("IS"));
    }
}
