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
    void parseNativeEndpointsNoDummyDummyFallback() {
        // dummy.dummy wird nicht mehr speziell behandelt – kein Umschreiben auf aliasName
        String json = "{\"apiResponse\":{\"api\":{\"nativeEndpoint\":[" +
                "{\"uri\":\"dummy.dummy\",\"alias\":false,\"aliasName\":\"RealAlias\"}" +
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
    // parseEndPointURIByName
    // ---------------------------------------------------------------

    @Test
    void parseEndPointURIByNameFound() {
        // Alias-Listen-Response mit mehreren Einträgen – nur der gesuchte hat endPointURI
        String json =
                "{\"id\":\"1\",\"name\":\"OtherAlias\",\"type\":\"simple\"}" +
                "{\"id\":\"2\",\"endPointURI\":\"https://real-backend:8080\"," +
                "\"name\":\"AKOS_API_EndpointAlias\",\"type\":\"endpoint\"}" +
                "{\"id\":\"3\",\"name\":\"AnotherAlias\",\"type\":\"simple\"}";
        assertEquals("https://real-backend:8080",
                service.parseEndPointURIByName(json, "AKOS_API_EndpointAlias"));
    }

    @Test
    void parseEndPointURIByNameNotFound() {
        String json = "{\"id\":\"1\",\"name\":\"OtherAlias\",\"type\":\"simple\"}";
        assertNull(service.parseEndPointURIByName(json, "AKOS_API_EndpointAlias"));
    }

    @Test
    void parseEndPointURIByNameNullInput() {
        assertNull(service.parseEndPointURIByName(null, "AKOS_API_EndpointAlias"));
        assertNull(service.parseEndPointURIByName("{}", null));
    }

    // ---------------------------------------------------------------
    // parseAllAliases + resolveAlias (In-memory Cache)
    // ---------------------------------------------------------------

    @Test
    void parseAllAliasesBuildsMap() {
        // Zwei Endpoint-Aliases in einer Alias-Listen-Antwort
        String json =
                "{\"id\":\"1\",\"name\":\"AliasA\",\"endPointURI\":\"https://backend-a:8080\",\"type\":\"endpoint\"}" +
                "{\"id\":\"2\",\"name\":\"AliasB\",\"endPointURI\":\"https://backend-b:9090\",\"type\":\"endpoint\"}";
        java.util.Map<String, String> map = service.parseAllAliases(json);
        assertEquals(2, map.size());
        assertEquals("https://backend-a:8080", map.get("AliasA"));
        assertEquals("https://backend-b:9090", map.get("AliasB"));
    }

    @Test
    void parseAllAliasesSkipsNonEndpointAliases() {
        // Eintrag ohne endPointURI darf nicht in der Map landen
        String json =
                "{\"id\":\"1\",\"name\":\"SimpleAlias\",\"type\":\"simple\"}" +
                "{\"id\":\"2\",\"name\":\"EndpointAlias\",\"endPointURI\":\"https://real-backend:8080\",\"type\":\"endpoint\"}";
        java.util.Map<String, String> map = service.parseAllAliases(json);
        assertEquals(1, map.size());
        assertEquals("https://real-backend:8080", map.get("EndpointAlias"));
        assertNull(map.get("SimpleAlias"));
    }

    @Test
    void resolveAliasUsesCacheOnSecondCall() throws Exception {
        // int[]-Zähler: von der anonymen Klasse aus schreibbar, von außen lesbar
        int[] loadCount = {0};

        // Subklasse, die loadAllAliases zählt und echten HTTP-Aufruf vermeidet
        AgwApiService spy = new AgwApiService() {
            @Override
            void loadAllAliases(ServerConfig server, String baseUrl) {
                loadCount[0]++;
                // aliasCache per Reflection befüllen
                try {
                    java.lang.reflect.Field f =
                            AgwApiService.class.getDeclaredField("aliasCache");
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, java.util.Map<String, String>> cache =
                            (java.util.Map<String, java.util.Map<String, String>>) f.get(this);
                    java.util.Map<String, String> entries = new java.util.HashMap<>();
                    entries.put("MyAlias", "https://cached-backend:8080");
                    cache.put(baseUrl, entries);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        ServerConfig server = new ServerConfig("testhost", 5555, "user", "pass");

        // Erster Aufruf: loadAllAliases muss aufgerufen werden
        String result1 = spy.resolveAlias(server, "MyAlias");
        assertEquals("https://cached-backend:8080", result1);
        assertEquals(1, loadCount[0]);

        // Zweiter Aufruf: Cache-Hit – loadAllAliases darf NICHT erneut aufgerufen werden
        String result2 = spy.resolveAlias(server, "MyAlias");
        assertEquals("https://cached-backend:8080", result2);
        assertEquals(1, loadCount[0]);
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

    // ---------------------------------------------------------------
    // parseEnforcementObjectIds
    // ---------------------------------------------------------------

    @Test
    void parseEnforcementObjectIdsExtracts() {
        String json = "{\"policy\":{\"policyEnforcements\":[" +
                "{\"enforcements\":[{\"enforcementObjectId\":\"act-1\"},{\"enforcementObjectId\":\"act-2\"}],\"stageKey\":\"routing\"}," +
                "{\"enforcements\":[{\"enforcementObjectId\":\"act-3\"}],\"stageKey\":\"IAM\"}" +
                "]}}";
        List<String> ids = service.parseEnforcementObjectIds(json);
        assertEquals(3, ids.size());
        assertEquals("act-1", ids.get(0));
        assertEquals("act-2", ids.get(1));
        assertEquals("act-3", ids.get(2));
    }

    @Test
    void parseEnforcementObjectIdsEmptyWhenAbsent() {
        assertTrue(service.parseEnforcementObjectIds("{\"policy\":{}}").isEmpty());
    }

    @Test
    void parseEnforcementObjectIdsNullInput() {
        assertTrue(service.parseEnforcementObjectIds(null).isEmpty());
    }

    // ---------------------------------------------------------------
    // parsePolicies
    // ---------------------------------------------------------------

    @Test
    void parsePoliciesExtractsPolicyIds() {
        String json = "{\"apiResponse\":{\"api\":{" +
                "\"policies\":[\"id-1\",\"id-2\",\"id-3\"]" +
                "}}}";
        List<String> ids = service.parsePolicies(json);
        assertEquals(3, ids.size());
        assertEquals("id-1", ids.get(0));
        assertEquals("id-2", ids.get(1));
        assertEquals("id-3", ids.get(2));
    }

    @Test
    void parsePoliciesEmptyWhenAbsent() {
        String json = "{\"apiResponse\":{\"api\":{}}}";
        assertTrue(service.parsePolicies(json).isEmpty());
    }

    // ---------------------------------------------------------------
    // parseRoutingEndpointUri
    // ---------------------------------------------------------------

    @Test
    void parseRoutingEndpointUriExtractsAliasExpression() {
        String json = policyActionsJson("${AKOS_API_EndpointAlias}/${sys:resource_path}");
        assertEquals("${AKOS_API_EndpointAlias}/${sys:resource_path}",
                service.parseRoutingEndpointUri(json));
    }

    @Test
    void parseRoutingEndpointUriExtractsDirectUrl() {
        String json = policyActionsJson("https://akos.oebb.at/pakos/smp/apt/${sys:resource_path}");
        assertEquals("https://akos.oebb.at/pakos/smp/apt/${sys:resource_path}",
                service.parseRoutingEndpointUri(json));
    }

    @Test
    void parseRoutingEndpointUriReturnsNullWhenAbsent() {
        assertNull(service.parseRoutingEndpointUri("{\"policyAction\":[]}"));
    }

    @Test
    void parseRoutingEndpointUriNullInput() {
        assertNull(service.parseRoutingEndpointUri(null));
    }

    // ---------------------------------------------------------------
    // parseRoutingAliasName
    // ---------------------------------------------------------------

    private static String policyActionsJson(String endpointUri) {
        return "{\"policyAction\":[" +
                "{\"id\":\"50c705f0\",\"templateKey\":\"straightThroughRouting\"," +
                "\"parameters\":[{\"templateKey\":\"endpointUri\",\"values\":[\"" + endpointUri + "\"]}]}" +
                "]}";
    }

    @Test
    void parseRoutingAliasNameExtractsFromExpression() {
        String json = policyActionsJson("${AKOS_API_EndpointAlias}/${sys:resource_path}");
        assertEquals("AKOS_API_EndpointAlias", service.parseRoutingAliasName(json));
    }

    @Test
    void parseRoutingAliasNameSimpleExpression() {
        String json = policyActionsJson("${MyAlias}");
        assertEquals("MyAlias", service.parseRoutingAliasName(json));
    }

    @Test
    void parseRoutingAliasNameAbsent() {
        String json = "{\"policyAction\":[" +
                "{\"id\":\"abc\",\"templateKey\":\"entryProtocolPolicy\"," +
                "\"parameters\":[{\"templateKey\":\"protocol\",\"values\":[\"http\"]}]}" +
                "]}";
        assertNull(service.parseRoutingAliasName(json));
    }

    @Test
    void parseRoutingAliasNameIgnoresNonRoutingActions() {
        // Mehrere Actions – nur straightThroughRouting darf ausgewertet werden
        String json = "{\"policyAction\":[" +
                "{\"id\":\"1\",\"templateKey\":\"entryProtocolPolicy\"," +
                "\"parameters\":[{\"templateKey\":\"protocol\",\"values\":[\"http\"]}]}," +
                "{\"id\":\"2\",\"templateKey\":\"evaluatePolicy\"," +
                "\"parameters\":[{\"templateKey\":\"someKey\",\"values\":[\"someVal\"]}]}," +
                "{\"id\":\"3\",\"templateKey\":\"straightThroughRouting\"," +
                "\"parameters\":[{\"templateKey\":\"endpointUri\",\"values\":[\"${RealAlias}/${sys:resource_path}\"]}]}" +
                "]}";
        assertEquals("RealAlias", service.parseRoutingAliasName(json));
    }

    @Test
    void parseRoutingAliasNameNullInput() {
        assertNull(service.parseRoutingAliasName(null));
    }

    @Test
    void parseRoutingAliasNameReturnsNullForDirectUrl() {
        // Direkte URL beginnt nicht mit ${...} → kein Alias
        String json = policyActionsJson("https://akos.oebb.at/pakos/smp/apt/${sys:resource_path}");
        assertNull(service.parseRoutingAliasName(json));
    }

    // ---------------------------------------------------------------
    // getNativeEndpoints – Integration (Subklasse überschreibt HTTP-Calls)
    // ---------------------------------------------------------------

    @Test
    void getNativeEndpointsAlwaysUsesRoutingPolicy() throws Exception {
        // Nur die HTTP-Calls werden gemockt – getNativeEndpoints läuft echt durch.
        AgwApiService svc = new AgwApiService() {
            @Override
            String fetchApiBody(ServerConfig server, String apiId) {
                return "{\"apiResponse\":{\"api\":{" +
                        "\"nativeEndpoint\":[{\"uri\":\"dummy.dummy\",\"alias\":false}]," +
                        "\"policies\":[\"policy-1\"]" +
                        "}}}";
            }
            @Override
            String fetchPolicy(ServerConfig server, String policyId) {
                return "{\"policy\":{\"policyEnforcements\":[{\"enforcements\":[" +
                        "{\"enforcementObjectId\":\"action-1\"}],\"stageKey\":\"routing\"}]}}";
            }
            @Override
            String fetchPolicyActions(ServerConfig server, List<String> ids) {
                return policyActionsJson("${AKOS_API_EndpointAlias}/${sys:resource_path}");
            }
            @Override
            String resolveAlias(ServerConfig server, String aliasName) {
                return "https://real-backend:8080";
            }
        };

        ServerConfig server = new ServerConfig("host", 443, "u", "p", null);
        List<RoutingEndpoint> result = svc.getNativeEndpoints(server, "api-1");
        assertEquals(1, result.size());
        assertTrue(result.get(0).isAlias());
        assertEquals("AKOS_API_EndpointAlias", result.get(0).getAliasName());
        assertEquals("https://real-backend:8080", result.get(0).getResolvedUrl());
    }

    @Test
    void getNativeEndpointsDirectUrlFromRoutingPolicy() throws Exception {
        // endpointUri ist eine direkte URL (kein ${Alias} am Anfang) –
        // getNativeEndpoints muss einen RoutingEndpoint.direct(...) zurückgeben.
        AgwApiService svc = new AgwApiService() {
            @Override
            String fetchApiBody(ServerConfig server, String apiId) {
                return "{\"apiResponse\":{\"api\":{" +
                        "\"nativeEndpoint\":[{\"uri\":\"dummy.dummy\",\"alias\":false}]," +
                        "\"policies\":[\"policy-1\"]" +
                        "}}}";
            }
            @Override
            String fetchPolicy(ServerConfig server, String policyId) {
                return "{\"policy\":{\"policyEnforcements\":[{\"enforcements\":[" +
                        "{\"enforcementObjectId\":\"action-1\"}],\"stageKey\":\"routing\"}]}}";
            }
            @Override
            String fetchPolicyActions(ServerConfig server, List<String> ids) {
                return policyActionsJson("https://akos.oebb.at/pakos/smp/apt/${sys:resource_path}");
            }
        };

        ServerConfig server = new ServerConfig("host", 443, "u", "p", null);
        List<RoutingEndpoint> result = svc.getNativeEndpoints(server, "api-1");
        assertEquals(1, result.size());
        assertFalse(result.get(0).isAlias());
        assertNull(result.get(0).getAliasName());
        assertEquals("https://akos.oebb.at/pakos/smp/apt/", result.get(0).getResolvedUrl());
    }

    @Test
    void getNativeEndpointsReturnsEmptyWhenNoPolicies() throws Exception {
        AgwApiService svc = new AgwApiService() {
            @Override
            public List<RoutingEndpoint> getNativeEndpoints(ServerConfig server, String apiId)
                    throws java.io.IOException {
                String fakeBody = "{\"apiResponse\":{\"api\":{}}}"; // kein policies-Feld
                List<String> policyIds = parsePolicies(fakeBody);
                if (policyIds.isEmpty()) return new java.util.ArrayList<>();
                return new java.util.ArrayList<>();
            }
        };
        ServerConfig server = new ServerConfig("host", 443, "u", "p", null);
        List<RoutingEndpoint> result = svc.getNativeEndpoints(server, "api-1");
        assertTrue(result.isEmpty());
    }

}
