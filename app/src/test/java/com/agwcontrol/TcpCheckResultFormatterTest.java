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
}
