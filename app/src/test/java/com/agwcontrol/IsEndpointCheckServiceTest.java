package com.agwcontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IsEndpointCheckService}.
 *
 * <p>All tests operate on the pure in-process logic (JSON parsing, URL encoding,
 * error result construction) and do not make any real network calls. Integration
 * with a live IS instance is out of scope for unit tests.</p>
 */
class IsEndpointCheckServiceTest {

    private static final IsEndpointCheckConfig CONFIG =
            new IsEndpointCheckConfig("http", "localhost", 5555, "admin", "manage");

    private final IsEndpointCheckService service = new IsEndpointCheckService(CONFIG);

    // --- parseCheckResponse — happy path ---

    @Test
    void parsesAllFieldsFromFullResponse() {
        String json = "{"
                + "\"url\":\"https://vm10477.org.oebb.at/MARS\","
                + "\"host\":\"vm10477.org.oebb.at\","
                + "\"resolved_ip\":\"10.66.24.18\","
                + "\"ping_reachable\":\"true\","
                + "\"ping_response_time\":\"12\","
                + "\"tcp_port\":\"443\","
                + "\"tcp_open\":\"true\","
                + "\"tcp_response_time\":\"8\","
                + "\"http_status\":\"401\","
                + "\"http_reachable\":\"true\","
                + "\"http_error_msg\":\"\""
                + "}";

        EndpointCheckResult r = service.parseCheckResponse("MyAPI", "v1", null,
                "https://vm10477.org.oebb.at/MARS", json);

        assertEquals("MyAPI", r.getApiName());
        assertEquals("v1",    r.getApiVersion());
        assertNull(r.getAliasName());
        assertEquals("https://vm10477.org.oebb.at/MARS", r.getUrl());
        assertEquals(401,   r.getHttpStatus());
        assertTrue(r.isReachable());
        assertEquals("",    r.getErrorMsg());
        assertTrue(r.isPingOk());
        assertEquals(12L,   r.getPingMs());
        assertTrue(r.isTcpOk());
        assertEquals(8L,    r.getTcpMs());
    }

    @Test
    void parsesAliasName() {
        String json = "{\"url\":\"https://host/api\",\"http_status\":\"200\","
                + "\"http_reachable\":\"true\",\"http_error_msg\":\"\","
                + "\"ping_reachable\":\"true\",\"ping_response_time\":\"5\","
                + "\"tcp_open\":\"true\",\"tcp_response_time\":\"3\"}";

        EndpointCheckResult r = service.parseCheckResponse("API", "1", "MY_ALIAS",
                "https://host/api", json);

        assertEquals("MY_ALIAS", r.getAliasName());
    }

    @Test
    void pingFalseAndMinusOneWhenPingUnreachable() {
        String json = "{\"url\":\"https://host/api\","
                + "\"ping_reachable\":\"false\",\"ping_response_time\":\"-1\","
                + "\"tcp_open\":\"false\",\"tcp_response_time\":\"-1\","
                + "\"http_status\":\"0\",\"http_reachable\":\"false\","
                + "\"http_error_msg\":\"Connection refused\"}";

        EndpointCheckResult r = service.parseCheckResponse("A", "1", null, "https://host/api", json);

        assertFalse(r.isPingOk());
        assertEquals(-1L, r.getPingMs());
        assertFalse(r.isTcpOk());
        assertEquals(-1L, r.getTcpMs());
        assertEquals(0,   r.getHttpStatus());
        assertFalse(r.isReachable());
        assertEquals("Connection refused", r.getErrorMsg());
    }

    // --- parseCheckResponse — missing / partial fields ---

    @Test
    void missingFieldsDefaultToSafeValues() {
        // Minimal JSON — only url present
        String json = "{\"url\":\"https://fallback.example.com/path\"}";

        EndpointCheckResult r = service.parseCheckResponse("API", "1", null,
                "https://fallback.example.com/path", json);

        assertEquals(0,     r.getHttpStatus());
        assertFalse(r.isReachable());
        assertEquals("",    r.getErrorMsg());
        assertFalse(r.isPingOk());
        assertEquals(-1L,   r.getPingMs());
        assertFalse(r.isTcpOk());
        assertEquals(-1L,   r.getTcpMs());
    }

    @Test
    void emptyJsonUsesCallSiteFallbackUrl() {
        EndpointCheckResult r = service.parseCheckResponse("API", "1", null,
                "https://original-url.example.com/path", "{}");

        // When "url" key is absent the json parser returns fallbackUrl
        assertEquals("https://original-url.example.com/path", r.getUrl());
    }

    @Test
    void nullJsonProducesErrorResult() {
        EndpointCheckResult r = service.parseCheckResponse("API", "1", null,
                "https://host/path", null);

        assertEquals(0,    r.getHttpStatus());
        assertFalse(r.isReachable());
        assertTrue(r.getErrorMsg() != null && !r.getErrorMsg().isEmpty());
    }

    @Test
    void emptyStringJsonProducesErrorResult() {
        EndpointCheckResult r = service.parseCheckResponse("API", "1", null,
                "https://host/path", "");

        assertEquals(0, r.getHttpStatus());
        assertFalse(r.isReachable());
        assertTrue(r.getErrorMsg() != null && !r.getErrorMsg().isEmpty());
    }

    // --- parseCheckResponse — type coercion ---

    @Test
    void nonNumericHttpStatusDefaultsToZero() {
        String json = "{\"url\":\"https://host\",\"http_status\":\"bad\","
                + "\"http_reachable\":\"false\",\"http_error_msg\":\"\","
                + "\"ping_reachable\":\"false\",\"ping_response_time\":\"0\","
                + "\"tcp_open\":\"false\",\"tcp_response_time\":\"0\"}";

        EndpointCheckResult r = service.parseCheckResponse("A", "1", null, "https://host", json);
        assertEquals(0, r.getHttpStatus());
    }

    @Test
    void caseInsensitiveTrueFalse() {
        String json = "{\"url\":\"https://host\","
                + "\"ping_reachable\":\"TRUE\",\"ping_response_time\":\"9\","
                + "\"tcp_open\":\"False\",\"tcp_response_time\":\"-1\","
                + "\"http_status\":\"200\",\"http_reachable\":\"True\","
                + "\"http_error_msg\":\"\"}";

        EndpointCheckResult r = service.parseCheckResponse("A", "1", null, "https://host", json);

        assertTrue(r.isPingOk());
        assertFalse(r.isTcpOk());
        assertTrue(r.isReachable());
    }

    // --- Constructor guard ---

    @Test
    void nullConfigThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckService(null));
    }

    // --- check() returns non-null on unreachable IS ---

    @Test
    void checkReturnsErrorResultWhenIsUnreachable() {
        // Port 1 is guaranteed closed; IS call will fail with IOException
        IsEndpointCheckConfig badConfig =
                new IsEndpointCheckConfig("http", "127.0.0.1", 1, "u", "p");
        IsEndpointCheckService svc = new IsEndpointCheckService(badConfig);

        EndpointCheckResult r = svc.check("API", "v1", "https://backend.example.com/path");

        assertNotNull(r);
        assertEquals("API", r.getApiName());
        assertEquals("v1",  r.getApiVersion());
        assertEquals(0,     r.getHttpStatus());
        assertFalse(r.isReachable());
        assertTrue(r.getErrorMsg() != null && !r.getErrorMsg().isEmpty(),
                "errorMsg must describe the IS connectivity failure");
    }
}
