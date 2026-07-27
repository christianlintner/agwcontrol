package com.agwcontrol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiDatabaseTest {

    private ApiDatabase db;

    @BeforeEach
    void setUp() throws SQLException {
        db = new ApiDatabase(":memory:");
        db.initSchema();
    }

    // ---------------------------------------------------------------
    // APIs
    // ---------------------------------------------------------------

    @Test
    void loadApisReturnsEmptyWhenNoneStored() throws SQLException {
        assertTrue(db.loadApis("PROD").isEmpty());
    }

    @Test
    void saveAndLoadApis() throws SQLException {
        List<ApiInfo> apis = List.of(
            new ApiInfo("id-1", "ApiA", "1.0", "REST", true),
            new ApiInfo("id-2", "ApiB", "2.0", "SOAP", false)
        );
        db.saveApis("PROD", apis);

        List<ApiInfo> loaded = db.loadApis("PROD");
        assertEquals(2, loaded.size());
        // Sortiert nach api_name
        assertEquals("ApiA", loaded.get(0).getName());
        assertEquals("ApiB", loaded.get(1).getName());
        assertEquals("id-1", loaded.get(0).getId());
        assertTrue(loaded.get(0).isActive());
        assertFalse(loaded.get(1).isActive());
    }

    @Test
    void saveApisOverwritesExistingEntries() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "ApiOld", "1.0", "REST", true)));
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "ApiNew", "2.0", "REST", false)));

        List<ApiInfo> loaded = db.loadApis("PROD");
        assertEquals(1, loaded.size());
        assertEquals("ApiNew", loaded.get(0).getName());
        assertFalse(loaded.get(0).isActive());
    }

    @Test
    void apisAreIsolatedByEnvironment() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "ProdApi", "1.0", "REST", true)));
        db.saveApis("DEV",  List.of(new ApiInfo("id-2", "DevApi",  "1.0", "REST", true)));

        assertEquals(1, db.loadApis("PROD").size());
        assertEquals("ProdApi", db.loadApis("PROD").get(0).getName());
        assertEquals(1, db.loadApis("DEV").size());
        assertEquals("DevApi", db.loadApis("DEV").get(0).getName());
    }

    // ---------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------

    @Test
    void loadEndpointsReturnsEmptyWhenNoneStored() throws SQLException {
        assertTrue(db.loadEndpoints("PROD", "id-1").isEmpty());
    }

    @Test
    void saveAndLoadDirectEndpoint() throws SQLException {
        List<RoutingEndpoint> eps = List.of(RoutingEndpoint.direct("https://backend:8080/svc"));
        db.saveEndpoints("PROD", "id-1", eps);

        List<RoutingEndpoint> loaded = db.loadEndpoints("PROD", "id-1");
        assertEquals(1, loaded.size());
        assertFalse(loaded.get(0).isAlias());
        assertEquals("https://backend:8080/svc", loaded.get(0).getResolvedUrl());
        assertNull(loaded.get(0).getAliasName());
    }

    @Test
    void saveAndLoadAliasEndpoint() throws SQLException {
        List<RoutingEndpoint> eps = List.of(RoutingEndpoint.alias("MyAlias", "https://resolved:9090"));
        db.saveEndpoints("PROD", "id-1", eps);

        List<RoutingEndpoint> loaded = db.loadEndpoints("PROD", "id-1");
        assertEquals(1, loaded.size());
        assertTrue(loaded.get(0).isAlias());
        assertEquals("MyAlias", loaded.get(0).getAliasName());
        assertEquals("https://resolved:9090", loaded.get(0).getResolvedUrl());
    }

    @Test
    void saveEndpointsOverwritesExistingEntries() throws SQLException {
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://old:8080")));
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://new:8080")));

        List<RoutingEndpoint> loaded = db.loadEndpoints("PROD", "id-1");
        assertEquals(1, loaded.size());
        assertEquals("https://new:8080", loaded.get(0).getResolvedUrl());
    }

    @Test
    void endpointsAreIsolatedByEnvironmentAndApi() throws SQLException {
        db.saveEndpoints("PROD", "id-1", List.of(RoutingEndpoint.direct("https://prod:8080")));
        db.saveEndpoints("DEV",  "id-1", List.of(RoutingEndpoint.direct("https://dev:8080")));
        db.saveEndpoints("PROD", "id-2", List.of(RoutingEndpoint.direct("https://prod2:8080")));

        assertEquals("https://prod:8080",  db.loadEndpoints("PROD", "id-1").get(0).getResolvedUrl());
        assertEquals("https://dev:8080",   db.loadEndpoints("DEV",  "id-1").get(0).getResolvedUrl());
        assertEquals("https://prod2:8080", db.loadEndpoints("PROD", "id-2").get(0).getResolvedUrl());
    }

    @Test
    void saveMultipleEndpointsPerApi() throws SQLException {
        List<RoutingEndpoint> eps = List.of(
            RoutingEndpoint.direct("https://backend1:8080"),
            RoutingEndpoint.alias("MyAlias", "https://alias-resolved:9090")
        );
        db.saveEndpoints("PROD", "id-1", eps);

        List<RoutingEndpoint> loaded = db.loadEndpoints("PROD", "id-1");
        assertEquals(2, loaded.size());
    }

    // ---------------------------------------------------------------
    // Environments
    // ---------------------------------------------------------------

    @Test
    void loadEnvironmentsReturnsDistinctSorted() throws SQLException {
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "ApiA", "1.0", "REST", true)));
        db.saveApis("DEV",  List.of(new ApiInfo("id-2", "ApiB", "1.0", "REST", true)));
        db.saveApis("TEST", List.of(new ApiInfo("id-3", "ApiC", "1.0", "REST", true)));
        // PROD noch einmal – darf nur einmal erscheinen
        db.saveApis("PROD", List.of(new ApiInfo("id-4", "ApiD", "1.0", "REST", true)));

        List<String> envs = db.loadEnvironments();
        assertEquals(List.of("DEV", "PROD", "TEST"), envs);
    }

    @Test
    void loadEnvironmentsEmptyWhenNoData() throws SQLException {
        assertTrue(db.loadEnvironments().isEmpty());
    }

    // ---------------------------------------------------------------
    // Check-Ergebnisse
    // ---------------------------------------------------------------

    @Test
    void saveAndLoadCheckResult() throws SQLException {
        EndpointCheckResult r = new EndpointCheckResult(
            "MyApi", "v1", "MyAlias", "https://backend/api",
            200, true, null,
            true, 12L, true, 8L
        );
        db.saveCheckResult("PROD", "id-1", "vm30073", r);

        List<EndpointCheckResult> loaded = db.loadCheckResults("PROD", "id-1");
        assertEquals(1, loaded.size());
        EndpointCheckResult l = loaded.get(0);
        assertEquals("https://backend/api", l.getUrl());
        assertEquals("MyAlias", l.getAliasName());
        assertTrue(l.isPingOk());
        assertEquals(12L, l.getPingMs());
        assertTrue(l.isTcpOk());
        assertEquals(8L, l.getTcpMs());
        assertEquals(200, l.getHttpStatus());
        assertTrue(l.isReachable());
        assertNull(l.getErrorMsg());
    }

    @Test
    void saveCheckResultOverwritesExisting() throws SQLException {
        EndpointCheckResult first = new EndpointCheckResult(
            "Api", "v1", null, "https://backend/api",
            200, true, null, true, 10L, true, 5L
        );
        EndpointCheckResult second = new EndpointCheckResult(
            "Api", "v1", null, "https://backend/api",
            500, false, "Server Error", false, -1L, true, 3L
        );
        db.saveCheckResult("PROD", "id-1", "vm30073", first);
        db.saveCheckResult("PROD", "id-1", "vm30073", second);

        List<EndpointCheckResult> loaded = db.loadCheckResults("PROD", "id-1");
        assertEquals(1, loaded.size());
        assertEquals(500, loaded.get(0).getHttpStatus());
        assertFalse(loaded.get(0).isReachable());
        assertEquals("Server Error", loaded.get(0).getErrorMsg());
    }

    @Test
    void loadCheckResultsEmptyWhenNoData() throws SQLException {
        assertTrue(db.loadCheckResults("PROD", "id-1").isEmpty());
    }

    @Test
    void checkResultsIsolatedByEnvironmentAndApi() throws SQLException {
        EndpointCheckResult r = new EndpointCheckResult(
            "Api", "v1", null, "https://backend/api",
            200, true, null, true, 5L, true, 3L
        );
        db.saveCheckResult("PROD", "id-1", "vm30073", r);
        db.saveCheckResult("DEV",  "id-1", "vm30073", r);
        db.saveCheckResult("PROD", "id-2", "vm30073", r);

        assertEquals(1, db.loadCheckResults("PROD", "id-1").size());
        assertEquals(1, db.loadCheckResults("DEV",  "id-1").size());
        assertEquals(1, db.loadCheckResults("PROD", "id-2").size());
        assertTrue(db.loadCheckResults("TEST", "id-1").isEmpty());
    }

    @Test
    void checkResultErrorFieldsStoredCorrectly() throws SQLException {
        EndpointCheckResult r = new EndpointCheckResult(
            "Api", "v1", null, "dummy.dummy",
            0, false, "Ungültige URL: no protocol: dummy.dummy",
            false, -1L, false, -1L
        );
        db.saveCheckResult("PROD", "id-1", "vm30073", r);

        EndpointCheckResult l = db.loadCheckResults("PROD", "id-1").get(0);
        assertFalse(l.isPingOk());
        assertEquals(-1L, l.getPingMs());
        assertFalse(l.isTcpOk());
        assertEquals(-1L, l.getTcpMs());
        assertEquals(0, l.getHttpStatus());
        assertFalse(l.isReachable());
        assertEquals("Ungültige URL: no protocol: dummy.dummy", l.getErrorMsg());
    }
}
