package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TcpCheckResultFormatterTest {

    @Test
    void formatOpenAndClosed() {
        List<TcpCheckResult> results = List.of(
                new TcpCheckResult("vm30073.linux.gleis.at", 443, true, 45),
                new TcpCheckResult("vm30074.linux.gleis.at", 443, false, -1)
        );
        String output = new TcpCheckResultFormatter().format(results);
        String[] lines = output.split(System.lineSeparator());
        assertEquals(2, lines.length);

        // Erste Zeile: OPEN mit Zeitangabe
        assertTrue(lines[0].contains("vm30073.linux.gleis.at:443"));
        assertTrue(lines[0].contains("OPEN"));
        assertTrue(lines[0].contains("45ms"));

        // Zweite Zeile: CLOSED ohne Zeitangabe
        assertTrue(lines[1].contains("vm30074.linux.gleis.at:443"));
        assertTrue(lines[1].contains("CLOSED"));
        assertTrue(lines[1].contains("-"));
    }

    @Test
    void emptyList() {
        assertEquals("", new TcpCheckResultFormatter().format(List.of()));
    }

    @Test
    void formatShowsLabelColumnWhenAnyLabelSet() {
        List<TcpCheckResult> results = List.of(
                new TcpCheckResult("agw-node.example.com",     443, "AGW",          true,  10),
                new TcpCheckResult("is-node.example.com",      443, "IS",           true,  12),
                new TcpCheckResult("cluster.example.com",      443, "CLUSTER",      false, -1),
                new TcpCheckResult("cluster-cert.example.com", 443, "CLUSTER-CERT", false, -1)
        );

        String output = new TcpCheckResultFormatter().format(results);
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
        List<TcpCheckResult> results = List.of(
                new TcpCheckResult("host-a.example.com", 443, true,  5),
                new TcpCheckResult("host-b.example.com", 443, false, -1)
        );
        String output = new TcpCheckResultFormatter().format(results);
        assertFalse(output.contains("AGW"));
        assertFalse(output.contains("IS"));
    }
}
