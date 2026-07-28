package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgwApiServiceTest {

    private final AgwApiService service = new AgwApiService();

    // ---------------------------------------------------------------
    // parseApis – Unit-Tests ohne Netzwerkzugriff
    // ---------------------------------------------------------------

    @Test
    void parsesEmptyApiResponse() {
        String json = "{\"apiResponse\":[]}";
        List<ApiInfo> result = service.parseApis(json);
        assertTrue(result.isEmpty());
    }

    @Test
    void parsesSingleActiveApi() {
        String json = "{\n" +
                "  \"apiResponse\": [{\n" +
                "    \"api\": {\n" +
                "      \"apiName\": \"ChuckNorrisAPI\",\n" +
                "      \"apiVersion\": \"1.0\",\n" +
                "      \"type\": \"REST\",\n" +
                "      \"isActive\": true,\n" +
                "      \"id\": \"abc-123\"\n" +
                "    },\n" +
                "    \"responseStatus\": \"SUCCESS\"\n" +
                "  }]\n" +
                "}";
        List<ApiInfo> result = service.parseApis(json);
        assertEquals(1, result.size());
        ApiInfo api = result.get(0);
        assertEquals("ChuckNorrisAPI", api.getName());
        assertEquals("1.0", api.getVersion());
        assertEquals("REST", api.getType());
        assertTrue(api.isActive());
        assertEquals("abc-123", api.getId());
    }

    @Test
    void parsesInactiveApi() {
        String json = "{\n" +
                "  \"apiResponse\": [{\n" +
                "    \"api\": {\n" +
                "      \"apiName\": \"OldService\",\n" +
                "      \"apiVersion\": \"2.0\",\n" +
                "      \"type\": \"SOAP\",\n" +
                "      \"isActive\": false,\n" +
                "      \"id\": \"def-456\"\n" +
                "    },\n" +
                "    \"responseStatus\": \"SUCCESS\"\n" +
                "  }]\n" +
                "}";
        List<ApiInfo> result = service.parseApis(json);
        assertEquals(1, result.size());
        assertFalse(result.get(0).isActive());
    }

    @Test
    void parsesMultipleApis() {
        String json = "{\n" +
                "  \"apiResponse\": [\n" +
                "    {\"api\":{\"apiName\":\"API-A\",\"apiVersion\":\"1\",\"type\":\"REST\",\"isActive\":true,\"id\":\"1\"},\"responseStatus\":\"SUCCESS\"},\n" +
                "    {\"api\":{\"apiName\":\"API-B\",\"apiVersion\":\"2\",\"type\":\"SOAP\",\"isActive\":false,\"id\":\"2\"},\"responseStatus\":\"SUCCESS\"}\n" +
                "  ]\n" +
                "}";
        List<ApiInfo> result = service.parseApis(json);
        assertEquals(2, result.size());
        assertEquals("API-A", result.get(0).getName());
        assertEquals("API-B", result.get(1).getName());
    }

    // ---------------------------------------------------------------
    // resolveBaseUrl
    // ---------------------------------------------------------------

    @Test
    void usesIsUrlWhenPresent() {
        ServerConfig server = new ServerConfig("vm40757.linux.oebb.at", 443,
                "user", "pass", "https://apigateway-is.oebb.at:443");
        assertEquals("https://apigateway-is.oebb.at:443", service.resolveBaseUrl(server));
    }

    @Test
    void stripsTrailingSlashFromIsUrl() {
        ServerConfig server = new ServerConfig("host", 443,
                "user", "pass", "https://apigateway-is.oebb.at:443/");
        assertEquals("https://apigateway-is.oebb.at:443", service.resolveBaseUrl(server));
    }

    @Test
    void fallsBackToHostPortWhenIsUrlNull() {
        ServerConfig server = new ServerConfig("vm40757.linux.oebb.at", 443, "user", "pass", null);
        assertEquals("https://vm40757.linux.oebb.at:443", service.resolveBaseUrl(server));
    }

    @Test
    void fallsBackToHostPortWhenIsUrlEmpty() {
        ServerConfig server = new ServerConfig("vm40757.linux.oebb.at", 8443, "user", "pass", "");
        assertEquals("https://vm40757.linux.oebb.at:8443", service.resolveBaseUrl(server));
    }

    // ---------------------------------------------------------------
    // parseNativeEndpoints
    // ---------------------------------------------------------------

    @Test
    void parseNativeEndpointsEmptyWhenFieldAbsent() {
        String json = "{\"apiResponse\":{\"api\":{},\"responseStatus\":\"SUCCESS\"}}";
        assertTrue(service.parseNativeEndpoints(json).isEmpty());
    }

    @Test
    void parseNativeEndpointsDirectUrl() {
        String json = "{\"apiResponse\":{\"api\":{\"nativeEndpoint\":[" +
                "{\"uri\":\"https://backend:8080/service\",\"alias\":false,\"connectionTimeoutDuration\":0}" +
                "]}}}";
        List<AgwApiService.NativeEndpointEntry> eps = service.parseNativeEndpoints(json);
        assertEquals(1, eps.size());
        assertEquals("https://backend:8080/service", eps.get(0).uri);
        assertFalse(eps.get(0).alias);
    }

    @Test
    void parseNativeEndpointsAliasTrue() {
        String json = "{\"apiResponse\":{\"api\":{\"nativeEndpoint\":[" +
                "{\"uri\":\"MystageEndpoint\",\"alias\":true,\"connectionTimeoutDuration\":0}" +
                "]}}}";
        List<AgwApiService.NativeEndpointEntry> eps = service.parseNativeEndpoints(json);
        assertEquals(1, eps.size());
        assertEquals("MystageEndpoint", eps.get(0).uri);
        assertTrue(eps.get(0).alias);
    }

    @Test
    void parseNativeEndpointsMultipleEntries() {
        String json = "{\"apiResponse\":{\"api\":{\"nativeEndpoint\":[" +
                "{\"uri\":\"https://a:8080/svc\",\"alias\":false}," +
                "{\"uri\":\"MyAlias\",\"alias\":true}" +
                "]}}}";
        List<AgwApiService.NativeEndpointEntry> eps = service.parseNativeEndpoints(json);
        assertEquals(2, eps.size());
        assertFalse(eps.get(0).alias);
        assertTrue(eps.get(1).alias);
    }

    @Test
    void parseNativeEndpointsUsesAliasNameWhenUriIsDummy() {
        String json = "{\"apiResponse\":{\"api\":{\"nativeEndpoint\":[" +
                "{\"uri\":\"dummy.dummy\",\"alias\":false,\"aliasName\":\"RealAlias\"}" +
                "]}}}";
        List<AgwApiService.NativeEndpointEntry> eps = service.parseNativeEndpoints(json);
        assertEquals(1, eps.size());
        assertEquals("RealAlias", eps.get(0).uri);
        assertTrue(eps.get(0).alias);
    }

    @Test
    void parseNativeEndpointsKeepsDirectUriWhenNoAliasNameExists() {
        String json = "{\"apiResponse\":{\"api\":{\"nativeEndpoint\":[" +
                "{\"uri\":\"dummy.dummy\",\"alias\":false}" +
                "]}}}";
        List<AgwApiService.NativeEndpointEntry> eps = service.parseNativeEndpoints(json);
        assertEquals(1, eps.size());
        assertEquals("dummy.dummy", eps.get(0).uri);
        assertFalse(eps.get(0).alias);
    }

    // ---------------------------------------------------------------
    // parseEndPointURI
    // ---------------------------------------------------------------

    @Test
    void parseEndPointURIFound() {
        String json = "{\"id\":\"abc\",\"endPointURI\":\"https://myDevstage:9090\",\"name\":\"MystageEndpoint\",\"type\":\"endpoint\"}";
        assertEquals("https://myDevstage:9090", service.parseEndPointURI(json));
    }

    @Test
    void parseEndPointURIAbsent() {
        String json = "{\"id\":\"abc\",\"name\":\"SomeAlias\",\"type\":\"simple\"}";
        assertNull(service.parseEndPointURI(json));
    }

    // ---------------------------------------------------------------
    // listApis mit DB-Cache
    // ---------------------------------------------------------------

    @Test
    void listApisReturnsDbDataWhenCacheEnabled() throws Exception {
        ApiDatabase db = new ApiDatabase(":memory:");
        db.initSchema();
        db.saveApis("PROD", List.of(new ApiInfo("id-1", "CachedApi", "1.0", "REST", true)));

        DbCacheConfig cache = new DbCacheConfig();
        cache.toggleAll(); // → useDb = true

        String[] hint = new String[1];
        List<ApiInfo> result = service.listApis(null, "PROD", db, cache, hint);

        assertEquals(1, result.size());
        assertEquals("CachedApi", result.get(0).getName());
        assertEquals("DB", hint[0]);
    }

    @Test
    void listApisFallsBackToServerWhenDbEmpty() throws Exception {
        // DB ist leer → Fallback auf Server erwartet
        // Da kein echter Server vorhanden ist, erwarten wir eine IOException
        ApiDatabase db = new ApiDatabase(":memory:");
        db.initSchema();

        DbCacheConfig cache = new DbCacheConfig();
        cache.toggleAll(); // → useDb = true, aber DB leer

        String[] hint = new String[1];
        assertThrows(Exception.class, () ->
            service.listApis(new ServerConfig("127.0.0.1", 1, "u", "p", "http://127.0.0.1:1"),
                             "PROD", db, cache, hint));
        // hint muss auf Fallback hinweisen
        assertEquals("Cache leer – lade vom Server", hint[0]);
    }

    @Test
    void listApisHintIsServerWhenCacheDisabled() throws Exception {
        ApiDatabase db = new ApiDatabase(":memory:");
        db.initSchema();

        DbCacheConfig cache = new DbCacheConfig(); // useDb = false

        String[] hint = new String[1];
        assertThrows(Exception.class, () ->
            service.listApis(new ServerConfig("127.0.0.1", 1, "u", "p", "http://127.0.0.1:1"),
                             "PROD", db, cache, hint));
        assertEquals("Server", hint[0]);
    }

    // ---------------------------------------------------------------
    // getNativeEndpoints mit DB-Cache
    // ---------------------------------------------------------------

    @Test
    void getNativeEndpointsReturnsDbDataWhenCacheEnabled() throws Exception {
        ApiDatabase db = new ApiDatabase(":memory:");
        db.initSchema();
        db.saveEndpoints("PROD", "id-1",
            List.of(RoutingEndpoint.direct("https://cached-backend:8080")));

        DbCacheConfig cache = new DbCacheConfig();
        cache.toggleAll(); // → useDb = true

        String[] hint = new String[1];
        List<RoutingEndpoint> result = service.getNativeEndpoints(
            null, "id-1", "PROD", db, cache, hint);

        assertEquals(1, result.size());
        assertEquals("https://cached-backend:8080", result.get(0).getResolvedUrl());
        assertEquals("DB", hint[0]);
    }

    @Test
    void getNativeEndpointsFallsBackWhenDbEmpty() throws Exception {
        ApiDatabase db = new ApiDatabase(":memory:");
        db.initSchema();

        DbCacheConfig cache = new DbCacheConfig();
        cache.toggleAll(); // → useDb = true, aber DB leer

        String[] hint = new String[1];
        assertThrows(Exception.class, () ->
            service.getNativeEndpoints(
                new ServerConfig("127.0.0.1", 1, "u", "p", "http://127.0.0.1:1"),
                "id-1", "PROD", db, cache, hint));
        assertEquals("Cache leer – lade vom Server", hint[0]);
    }

}
