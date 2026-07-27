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
}
