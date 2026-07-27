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
    void singleApiReachableShowsOkWithStatus() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("MyAPI", "1.0",
                        "http://host/ep", 200, true, ""));
        String out = formatter.format("srv", results);
        assertTrue(out.contains("OK (200)"));
        assertTrue(out.contains("HTTP Status"));
    }

    @Test
    void singleApiUnreachableShowsNeinWithError() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("MyAPI", "1.0",
                        "http://host/ep", 0, false, "Connection refused"));
        String out = formatter.format("srv", results);
        assertTrue(out.contains("FAIL (Connection refused)"));
    }

    @Test
    void singleApiUnreachableNoErrorShowsNein() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("MyAPI", "1.0",
                        "http://host/ep", 0, false, ""));
        assertTrue(formatter.format("srv", results).contains("FAIL"));
    }

    @Test
    void singleApiSummaryLineCorrect() {
        // pingOk=false, tcpOk=false → isOk=false; beide nicht OK
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("A", "1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("A", "1", "http://a/e2", 0, false, "timeout"));
        String out = formatter.format("srv", results);
        assertTrue(out.contains("Ergebnis: 0/2 OK"));
    }

    // ---------------------------------------------------------------
    // Alle-APIs-Modus (mehrere distinct apiNames)
    // ---------------------------------------------------------------

    @Test
    void multiApiHeaderShowsAlleApis() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("CustomerAPI", "v1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("OrderService", "v2", "http://a/e2", 404, true, ""));
        String out = formatter.format("agw.server.com", results);
        assertTrue(out.contains("alle APIs"));
        assertTrue(out.contains("agw.server.com"));
    }

    @Test
    void multiApiTableHasApiColumn() {
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("CustomerAPI", "v1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("OrderService", "v2", "http://a/e2", 404, true, ""));
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
        // Alle ohne Ping/TCP → isOk=false für alle
        List<EndpointCheckResult> results = List.of(
                new EndpointCheckResult("A", "1", "http://a/e1", 200, true, ""),
                new EndpointCheckResult("B", "2", "http://b/e1", 0, false, "err"),
                new EndpointCheckResult("C", "3", "http://c/e1", 503, true, ""));
        String out = formatter.format("srv", results);
        assertTrue(out.contains("Ergebnis: 0/3 OK"));
    }

    @Test
    void summaryCountsAllThreeChecks() {
        // pingOk=true, tcpOk=true, reachable=true → isOk=true
        EndpointCheckResult ok = new EndpointCheckResult(
                "A", "1", null, "http://a/e1", 200, true, "",
                true, 5L, true, 3L);
        // pingOk=false → isOk=false
        EndpointCheckResult fail = new EndpointCheckResult(
                "A", "1", null, "http://a/e2", 200, true, "",
                false, -1L, true, 3L);
        String out = formatter.format("srv", List.of(ok, fail));
        assertTrue(out.contains("Ergebnis: 1/2 OK"));
    }

    @Test
    void pingTcpColumnsAppearsWhenPresent() {
        EndpointCheckResult r = new EndpointCheckResult(
                "A", "1", null, "http://a/e1", 200, true, "",
                true, 12L, true, 8L);
        String out = formatter.format("srv", List.of(r));
        assertTrue(out.contains("Ping"));
        assertTrue(out.contains("TCP"));
        assertTrue(out.contains("OK 12ms"));
        assertTrue(out.contains("OPEN 8ms"));
    }

    @Test
    void pingTcpColumnsAbsentWhenDefault() {
        // Kein Ping/TCP gesetzt (alle -1/false) → Spalten sollen nicht erscheinen
        EndpointCheckResult r = new EndpointCheckResult("A", "1", "http://a/e1", 200, true, "");
        String out = formatter.format("srv", List.of(r));
        assertFalse(out.contains("Ping"));
        assertFalse(out.contains("TCP"));
    }
}
