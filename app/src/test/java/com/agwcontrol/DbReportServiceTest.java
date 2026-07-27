package com.agwcontrol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DbReportServiceTest {

    private ApiDatabase db;
    private DbReportService service;

    @BeforeEach
    void setUp() throws SQLException {
        db = new ApiDatabase(":memory:");
        db.initSchema();
        service = new DbReportService(db);
    }

    // ---------------------------------------------------------------
    // buildCsv – Struktur
    // ---------------------------------------------------------------

    @Test
    void csvContainsHeaderRow() throws SQLException {
        String csv = service.buildCsv("PROD");
        assertEquals(DbReportService.HEADER, csv.lines().findFirst().orElse(""));
    }

    @Test
    void csvEmptyDatabase() throws SQLException {
        String csv = service.buildCsv("PROD");
        // Nur Header-Zeile
        assertEquals(1, csv.lines().count());
    }

    @Test
    void csvContainsApiColumns() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "KundenAPI", "v1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1", List.of(RoutingEndpoint.direct("https://backend/api")));

        String csv = service.buildCsv("PROD");
        List<String> lines = csv.lines().collect(Collectors.toList());
        assertEquals(2, lines.size());
        String data = lines.get(1);
        assertTrue(data.startsWith("KundenAPI;v1.0;REST;true;"));
    }

    @Test
    void csvContainsEndpointUrl() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://backend.example.com/svc")));

        String csv = service.buildCsv("PROD");
        assertTrue(csv.contains("https://backend.example.com/svc"));
    }

    @Test
    void csvAliasNamePresentForAliasEndpoint() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.alias("MyAlias", "https://resolved.example.com")));

        String csv = service.buildCsv("PROD");
        // alias_name-Spalte (5. Feld) muss "MyAlias" enthalten
        String dataLine = csv.lines().skip(1).findFirst().orElse("");
        String[] cols = dataLine.split(";", -1);
        assertEquals("MyAlias", cols[4]);
        assertEquals("https://resolved.example.com", cols[5]);
    }

    @Test
    void csvAliasNameEmptyForDirectEndpoint() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://direct.example.com")));

        String csv = service.buildCsv("PROD");
        String dataLine = csv.lines().skip(1).findFirst().orElse("");
        String[] cols = dataLine.split(";", -1);
        assertEquals("", cols[4]);  // alias_name leer
    }

    // ---------------------------------------------------------------
    // buildCsv – Check-Ergebnisse
    // ---------------------------------------------------------------

    @Test
    void csvEmptyCheckColumnsWhenNoCheckStored() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://backend/api")));

        String csv = service.buildCsv("PROD");
        String dataLine = csv.lines().skip(1).findFirst().orElse("");
        // Zeile muss 15 Felder haben (Index 0-14), Check-Spalten (Index 6-14) leer
        String[] cols = dataLine.split(";", -1);
        assertEquals(15, cols.length);
        for (int i = 6; i < 14; i++) {
            assertEquals("", cols[i], "Spalte " + i + " muss leer sein");
        }
    }

    @Test
    void csvContainsCheckResultColumns() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://backend/api")));
        EndpointCheckResult r = new EndpointCheckResult(
            "Api", "1.0", null, "https://backend/api",
            200, true, null, true, 12L, true, 8L
        );
        db.saveCheckResult("PROD", "id-1", "vm30073", r);

        String csv = service.buildCsv("PROD");
        String dataLine = csv.lines().skip(1).findFirst().orElse("");
        String[] cols = dataLine.split(";", -1);
        assertEquals(15, cols.length);
        assertEquals("vm30073", cols[6]);   // server_host
        assertEquals("true",   cols[7]);    // ping_ok
        assertEquals("12",     cols[8]);    // ping_ms
        assertEquals("true",   cols[9]);    // tcp_ok
        assertEquals("8",      cols[10]);   // tcp_ms
        assertEquals("200",    cols[11]);   // http_status
        assertEquals("true",   cols[12]);   // reachable
        assertEquals("",       cols[13]);   // error_msg (null → leer)
    }

    @Test
    void csvOneRowPerServerPerEndpoint() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://backend/api")));
        EndpointCheckResult r1 = new EndpointCheckResult(
            "Api", "1.0", null, "https://backend/api",
            200, true, null, true, 5L, true, 3L
        );
        EndpointCheckResult r2 = new EndpointCheckResult(
            "Api", "1.0", null, "https://backend/api",
            0, false, "Connection refused", false, -1L, false, -1L
        );
        db.saveCheckResult("PROD", "id-1", "vm30073", r1);
        db.saveCheckResult("PROD", "id-1", "vm30074", r2);

        String csv = service.buildCsv("PROD");
        long dataLines = csv.lines().count() - 1;
        assertEquals(2, dataLines);
    }

    @Test
    void csvErrorFieldQuotedWhenContainsSemicolon() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://backend/api")));
        EndpointCheckResult r = new EndpointCheckResult(
            "Api", "1.0", null, "https://backend/api",
            0, false, "Fehler; kritisch", false, -1L, false, -1L
        );
        db.saveCheckResult("PROD", "id-1", "vm30073", r);

        String csv = service.buildCsv("PROD");
        assertTrue(csv.contains("\"Fehler; kritisch\""));
    }

    // ---------------------------------------------------------------
    // writeReports – Datei-Output
    // ---------------------------------------------------------------

    @Test
    void writeReportsCreatesFilePerEnvironment(@TempDir Path tmpDir)
            throws SQLException, IOException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveApis("TEST", List.of(new ApiInfo("id-2", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1", List.of(RoutingEndpoint.direct("https://p/api")));
        db.saveEndpoints("TEST", "id-2", List.of(RoutingEndpoint.direct("https://t/api")));

        int created = service.writeReports(tmpDir);
        // 2 Env-Dateien + 1 Cross-Env-Datei
        assertEquals(3, created);

        long csvFiles = Files.list(tmpDir)
            .filter(p -> p.getFileName().toString().endsWith(".csv"))
            .count();
        assertEquals(3, csvFiles);
    }

    @Test
    void writeReportsFilenameContainsEnvironmentAndTimestamp(@TempDir Path tmpDir)
            throws SQLException, IOException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "Api", "1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1", List.of(RoutingEndpoint.direct("https://p/api")));

        service.writeReports(tmpDir);

        boolean found = Files.list(tmpDir)
            .map(p -> p.getFileName().toString())
            .anyMatch(name -> name.startsWith("report_PROD_") && name.endsWith(".csv"));
        assertTrue(found, "Dateiname muss mit report_PROD_ beginnen und auf .csv enden");
    }

    @Test
    void writeReportsFileContainsHeaderAndData(@TempDir Path tmpDir)
            throws SQLException, IOException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "KundenAPI", "v1.0", "REST", true)));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://backend/api")));

        service.writeReports(tmpDir);

        Path csvFile = Files.list(tmpDir)
            .filter(p -> p.getFileName().toString().startsWith("report_PROD_"))
            .findFirst().orElseThrow();
        String content = Files.readString(csvFile);
        assertTrue(content.startsWith(DbReportService.HEADER));
        assertTrue(content.contains("KundenAPI"));
    }

    // ---------------------------------------------------------------
    // Cross-Environment Report
    // ---------------------------------------------------------------

    @Test
    void crossEnvHeaderContainsAllEnvironments() throws SQLException {
        db.saveApis("DN2020-DEV",     List.of(new ApiInfo("id-1", "Payments API", "v1", "REST", true)));
        db.saveApis("DN2020-PreProd", List.of(new ApiInfo("id-2", "Payments API", "v1", "REST", true)));
        db.saveEndpoints("DN2020-DEV",     "id-1", List.of(RoutingEndpoint.alias("gw1", "https://dev-gw1/pay")));
        db.saveEndpoints("DN2020-PreProd", "id-2", List.of(RoutingEndpoint.alias("gw1", "https://preprod-gw1/pay")));

        String csv = service.buildCrossEnvCsv();
        String headerLine = csv.lines().findFirst().orElse("");

        assertTrue(headerLine.startsWith(DbReportService.CROSS_ENV_HEADER_PREFIX));
        assertTrue(headerLine.contains("DN2020-DEV_alias_name"),     "Header muss DN2020-DEV_alias_name enthalten");
        assertTrue(headerLine.contains("DN2020-DEV_endpoint_url"),   "Header muss DN2020-DEV_endpoint_url enthalten");
        assertTrue(headerLine.contains("DN2020-PreProd_alias_name"), "Header muss DN2020-PreProd_alias_name enthalten");
        assertTrue(headerLine.contains("DN2020-PreProd_endpoint_url"));
    }

    @Test
    void crossEnvOneRowPerApi() throws SQLException {
        db.saveApis("DN2020-DEV",     List.of(new ApiInfo("id-1", "Payments API", "v1", "REST", true)));
        db.saveApis("DN2020-PreProd", List.of(new ApiInfo("id-2", "Payments API", "v1", "REST", true)));
        db.saveEndpoints("DN2020-DEV",     "id-1", List.of(RoutingEndpoint.alias("gw1", "https://dev-gw1/pay")));
        db.saveEndpoints("DN2020-PreProd", "id-2", List.of(RoutingEndpoint.alias("gw1", "https://preprod-gw1/pay")));

        EndpointCheckResult r1 = new EndpointCheckResult(
            "Payments API", "v1", "gw1", "https://dev-gw1/pay",
            200, true, null, true, 10L, true, 5L);
        EndpointCheckResult r2 = new EndpointCheckResult(
            "Payments API", "v1", "gw1", "https://preprod-gw1/pay",
            200, true, null, true, 12L, true, 6L);
        db.saveCheckResult("DN2020-DEV",     "id-1", "vm01", r1);
        db.saveCheckResult("DN2020-PreProd", "id-2", "vm02", r2);

        String csv = service.buildCrossEnvCsv();
        long dataLines = csv.lines().count() - 1;
        assertEquals(1, dataLines, "Es muss genau eine Datenzeile geben");

        String dataLine = csv.lines().skip(1).findFirst().orElse("");
        assertTrue(dataLine.contains("https://dev-gw1/pay"),     "DEV-URL muss in der Zeile stehen");
        assertTrue(dataLine.contains("https://preprod-gw1/pay"), "PreProd-URL muss in der Zeile stehen");
    }

    @Test
    void crossEnvEmptyColumnsForMissingEnvironment() throws SQLException {
        // DN2020-PreProd muss über eine andere API bekannt sein, damit loadEnvironments() sie liefert
        db.saveApis("DN2020-DEV",     List.of(new ApiInfo("id-1", "Payments API", "v1", "REST", true)));
        db.saveApis("DN2020-PreProd", List.of(new ApiInfo("id-x", "Other API",    "v1", "REST", true)));
        db.saveEndpoints("DN2020-DEV", "id-1", List.of(RoutingEndpoint.alias("gw1", "https://dev-gw1/pay")));
        // Kein Endpoint für Payments API in DN2020-PreProd

        EndpointCheckResult r = new EndpointCheckResult(
            "Payments API", "v1", "gw1", "https://dev-gw1/pay",
            200, true, null, true, 10L, true, 5L);
        db.saveCheckResult("DN2020-DEV", "id-1", "vm01", r);

        String csv = service.buildCrossEnvCsv();
        String dataLine = csv.lines()
            .skip(1)
            .filter(l -> l.startsWith("Payments API"))
            .findFirst().orElse("");

        // Header: 4 feste Felder + 10 pro Env (2 Envs) = 24 Felder → 23 Semikola
        String[] cols = dataLine.split(";", -1);
        assertEquals(24, cols.length, "Zeile muss 24 Felder haben (4 + 2×10)");

        // DN2020-PreProd-Block (Index 14–23) muss leer sein
        for (int i = 14; i < 24; i++) {
            assertEquals("", cols[i], "Feld " + i + " (PreProd-Block) muss leer sein");
        }
    }

    @Test
    void writeReportsCreatesCrossEnvFile(@TempDir Path tmpDir) throws SQLException, IOException {
        db.saveApis("DN2020-DEV",     List.of(new ApiInfo("id-1", "Api", "v1", "REST", true)));
        db.saveApis("DN2020-PreProd", List.of(new ApiInfo("id-2", "Api", "v1", "REST", true)));
        db.saveEndpoints("DN2020-DEV",     "id-1", List.of(RoutingEndpoint.direct("https://dev/api")));
        db.saveEndpoints("DN2020-PreProd", "id-2", List.of(RoutingEndpoint.direct("https://preprod/api")));

        int created = service.writeReports(tmpDir);

        // 2 Env-Dateien + 1 Cross-Env-Datei
        assertEquals(3, created);

        boolean found = Files.list(tmpDir)
            .map(p -> p.getFileName().toString())
            .anyMatch(name -> name.startsWith("report_all_environments_") && name.endsWith(".csv"));
        assertTrue(found, "report_all_environments_*.csv muss erstellt worden sein");
    }

    // ---------------------------------------------------------------
    // csvField
    // ---------------------------------------------------------------

    @Test
    void csvFieldNullReturnsEmpty() {
        assertEquals("", DbReportService.csvField(null));
    }

    @Test
    void csvFieldNormalValueUnchanged() {
        assertEquals("hello", DbReportService.csvField("hello"));
    }

    @Test
    void csvFieldWithSemicolonQuoted() {
        assertEquals("\"a;b\"", DbReportService.csvField("a;b"));
    }

    @Test
    void csvFieldWithQuoteEscaped() {
        assertEquals("\"say \"\"hi\"\"\"", DbReportService.csvField("say \"hi\""));
    }
}
