package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndpointCheckResultFormatterTest {

    private final EndpointCheckResultFormatter formatter = new EndpointCheckResultFormatter();

    @Test
    void emptyListReturnsNoneFound() {
        String result = formatter.format("srv", List.of());
        assertEquals("Keine Endpoints gefunden.", result);
    }

    // ---------------------------------------------------------------
    // Einzel-API-Modus
    // ---------------------------------------------------------------

    @Test
    void singleApiHeaderContainsApiNameAndServer() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("CustomerAPI", "v1",
                        "https://agw:443/gateway/CustomerAPI/v1", 200, true, ""));
        String out = formatter.format("agw.server.com", results);
        assertTrue(out.contains("CustomerAPI"));
        assertTrue(out.contains("v1"));
        assertTrue(out.contains("agw.server.com"));
    }

    @Test
    void singleApiNoApiColumnInTable() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("CustomerAPI", "v1",
                        "https://agw:443/gateway/CustomerAPI/v1", 200, true, ""));
        String out = formatter.format("srv", results);
        // In Einzel-API-Modus darf die Tabelle keine "API"-Spaltenüberschrift haben
        assertFalse(out.contains("  API ") || out.lines()
                .anyMatch(l -> l.trim().startsWith("API") && l.contains("URL")));
    }

    @Test
    void singleApiReachableShowsJa() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("MyAPI", "1.0",
                        "http://host/ep", 200, true, ""));
        assertTrue(formatter.format("srv", results).contains("JA"));
    }

    @Test
    void singleApiUnreachableShowsNeinWithError() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("MyAPI", "1.0",
                        "http://host/ep", 0, false, "Connection refused"));
        String out = formatter.format("srv", results);
        assertTrue(out.contains("NEIN"));
        assertTrue(out.contains("Connection refused"));
    }

    @Test
    void singleApiSummaryLineCorrect() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("A", "1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("A", "1", "http://a/e2", 0, false, "timeout"));
        String out = formatter.format("srv", results);
        assertTrue(out.contains("2 Endpoints geprüft"));
        assertTrue(out.contains("1 erreichbar"));
    }

    // ---------------------------------------------------------------
    // Alle-APIs-Modus (mehrere distinct apiNames)
    // ---------------------------------------------------------------

    @Test
    void multiApiHeaderShowsAlleApis() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("CustomerAPI", "v1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("OrderService", "v2", "http://a/e2", 404, false, ""));
        String out = formatter.format("agw.server.com", results);
        assertTrue(out.contains("alle APIs"));
        assertTrue(out.contains("agw.server.com"));
    }

    @Test
    void multiApiTableHasApiColumn() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("CustomerAPI", "v1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("OrderService", "v2", "http://a/e2", 404, false, ""));
        String out = formatter.format("srv", results);
        // Tabellenheader muss "API" enthalten
        assertTrue(out.contains("API"));
    }

    @Test
    void multiApiAllNamesAppearInOutput() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("CustomerAPI", "v1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("OrderService", "v2", "http://a/e2", 0, false, "timeout"));
        String out = formatter.format("srv", results);
        assertTrue(out.contains("CustomerAPI"));
        assertTrue(out.contains("OrderService"));
    }

    @Test
    void multiApiSummaryCorrect() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("A", "1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("B", "2", "http://b/e1", 0, false, "err"),
                new EndpointCheckResult("C", "3", "http://c/e1", 503, false, ""));
        String out = formatter.format("srv", results);
        assertTrue(out.contains("3 Endpoints geprüft"));
        assertTrue(out.contains("1 erreichbar"));
    }
}
