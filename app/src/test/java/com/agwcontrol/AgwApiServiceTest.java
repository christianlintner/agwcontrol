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
}
