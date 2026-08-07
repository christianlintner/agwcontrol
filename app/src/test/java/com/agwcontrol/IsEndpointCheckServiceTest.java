package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IsEndpointCheckService}.
 *
 * <p>All tests operate on pure in-process logic (JSON parsing, URL building,
 * sequential orchestration with early-exit) and do not make any real network
 * calls. Integration with a live IS instance is out of scope for unit tests.</p>
 */
class IsEndpointCheckServiceTest {

    private static final IsEndpointCheckConfig CONFIG =
            new IsEndpointCheckConfig("http", "localhost", 5555, "admin", "manage");

    private final IsEndpointCheckService service = new IsEndpointCheckService(CONFIG);

    // -----------------------------------------------------------------------
    // parsePingResponse
    // -----------------------------------------------------------------------

    @Test
    void parsePingResponse_reachableTrue() {
        // IS /ping response format: {"host":"...","reachable":"true","response_time":"1"}
        String json = "{\"host\":\"vm10477\",\"reachable\":\"true\",\"response_time\":\"12\"}";
        IsEndpointCheckService.PingProbeResult r = service.parsePingResponse(json);
        assertTrue(r.reachable);
        assertEquals(12L, r.responseTimeMs);
    }

    @Test
    void parsePingResponse_reachableFalse() {
        String json = "{\"host\":\"vm10477\",\"reachable\":\"false\",\"response_time\":\"-1\"}";
        IsEndpointCheckService.PingProbeResult r = service.parsePingResponse(json);
        assertFalse(r.reachable);
        assertEquals(-1L, r.responseTimeMs);
    }

    @Test
    void parsePingResponse_caseInsensitiveTrue() {
        String json = "{\"reachable\":\"TRUE\",\"response_time\":\"9\"}";
        assertTrue(service.parsePingResponse(json).reachable);
    }

    @Test
    void parsePingResponse_missingFieldsDefaultToFalse() {
        IsEndpointCheckService.PingProbeResult r = service.parsePingResponse("{}");
        assertFalse(r.reachable);
        assertEquals(-1L, r.responseTimeMs);
    }

    @Test
    void parsePingResponse_nullOrEmptyReturnsFail() {
        IsEndpointCheckService.PingProbeResult rNull  = service.parsePingResponse(null);
        IsEndpointCheckService.PingProbeResult rEmpty = service.parsePingResponse("");
        assertFalse(rNull.reachable);
        assertFalse(rEmpty.reachable);
        assertEquals(-1L, rNull.responseTimeMs);
        assertEquals(-1L, rEmpty.responseTimeMs);
    }

    // -----------------------------------------------------------------------
    // parseTcpResponse
    // -----------------------------------------------------------------------

    @Test
    void parseTcpResponse_open() {
        // IS /tcp response format: {"host":"...","port":"443","open":"true","response_time":"8"}
        String json = "{\"host\":\"vm10477\",\"port\":\"443\",\"open\":\"true\",\"response_time\":\"8\"}";
        IsEndpointCheckService.TcpProbeResult r = service.parseTcpResponse(json);
        assertTrue(r.open);
        assertEquals(8L, r.responseTimeMs);
    }

    @Test
    void parseTcpResponse_closed() {
        String json = "{\"host\":\"vm10477\",\"port\":\"443\",\"open\":\"false\",\"response_time\":\"-1\"}";
        IsEndpointCheckService.TcpProbeResult r = service.parseTcpResponse(json);
        assertFalse(r.open);
        assertEquals(-1L, r.responseTimeMs);
    }

    @Test
    void parseTcpResponse_caseInsensitiveFalse() {
        String json = "{\"open\":\"False\",\"response_time\":\"-1\"}";
        assertFalse(service.parseTcpResponse(json).open);
    }

    @Test
    void parseTcpResponse_missingFieldsDefaultToFalse() {
        IsEndpointCheckService.TcpProbeResult r = service.parseTcpResponse("{}");
        assertFalse(r.open);
        assertEquals(-1L, r.responseTimeMs);
    }

    @Test
    void parseTcpResponse_nullOrEmptyReturnsClosed() {
        assertFalse(service.parseTcpResponse(null).open);
        assertFalse(service.parseTcpResponse("").open);
    }

    // -----------------------------------------------------------------------
    // parseHttpResponse
    // -----------------------------------------------------------------------

    @Test
    void parseHttpResponse_fullResponse() {
        // IS /http response format: {"url":"...","http_status":"401","reachable":"true","error_msg":""}
        String json = "{\"url\":\"https://vm10477.org.oebb.at/MARS\","
                + "\"http_status\":\"401\","
                + "\"reachable\":\"true\","
                + "\"error_msg\":\"\"}";
        IsEndpointCheckService.HttpProbeResult r =
                service.parseHttpResponse(json, "https://fallback.example.com");
        assertEquals("https://vm10477.org.oebb.at/MARS", r.url);
        assertEquals(401, r.status);
        assertTrue(r.reachable);
        assertEquals("", r.errorMsg);
    }

    @Test
    void parseHttpResponse_useFallbackUrlWhenUrlMissing() {
        IsEndpointCheckService.HttpProbeResult r =
                service.parseHttpResponse("{}", "https://fallback.example.com/path");
        assertEquals("https://fallback.example.com/path", r.url);
    }

    @Test
    void parseHttpResponse_nonNumericStatusDefaultsToZero() {
        String json = "{\"http_status\":\"bad\",\"reachable\":\"false\",\"error_msg\":\"\"}";
        assertEquals(0, service.parseHttpResponse(json, "https://host").status);
    }

    @Test
    void parseHttpResponse_nullOrEmptyReturnsErrorResult() {
        IsEndpointCheckService.HttpProbeResult rNull  =
                service.parseHttpResponse(null,  "https://host/path");
        IsEndpointCheckService.HttpProbeResult rEmpty =
                service.parseHttpResponse("", "https://host/path");
        assertEquals(0, rNull.status);
        assertFalse(rNull.reachable);
        assertFalse(rNull.errorMsg.isEmpty());
        assertEquals(0, rEmpty.status);
        assertFalse(rEmpty.reachable);
        assertFalse(rEmpty.errorMsg.isEmpty());
    }

    @Test
    void parseHttpResponse_errorMsg() {
        String json = "{\"url\":\"https://host\",\"http_status\":\"0\","
                + "\"reachable\":\"false\","
                + "\"error_msg\":\"Connection refused\"}";
        assertEquals("Connection refused",
                service.parseHttpResponse(json, "https://host").errorMsg);
    }

    // -----------------------------------------------------------------------
    // parseResolveHostResponse
    // -----------------------------------------------------------------------

    @Test
    void parseResolveHostResponse_withIp() {
        // IS /resolveHost response format: {"host":"backend.example.com","resolved_ip":"10.0.1.42"}
        String json = "{\"host\":\"backend.example.com\",\"resolved_ip\":\"10.0.1.42\"}";
        assertEquals("10.0.1.42", service.parseResolveHostResponse(json));
    }

    @Test
    void parseResolveHostResponse_emptyIp() {
        String json = "{\"host\":\"\",\"resolved_ip\":\"\"}";
        assertNull(service.parseResolveHostResponse(json));
    }

    @Test
    void parseResolveHostResponse_missingResolvedIpField() {
        String json = "{\"host\":\"backend.example.com\"}";
        assertNull(service.parseResolveHostResponse(json));
    }

    @Test
    void parseResolveHostResponse_nullOrEmpty() {
        assertNull(service.parseResolveHostResponse(null));
        assertNull(service.parseResolveHostResponse(""));
    }

    // -----------------------------------------------------------------------
    // Constructor guard
    // -----------------------------------------------------------------------

    @Test
    void nullConfigThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckService(null));
    }

    // -----------------------------------------------------------------------
    // check() — Early-Exit scenarios (via package-private stub subclass)
    // -----------------------------------------------------------------------

    /**
     * Stub subclass that overrides the three IS-call methods so no real network
     * traffic is produced. Each method returns a pre-configured JSON string or
     * throws an IOException if instructed.
     */
    private static class StubIsService extends IsEndpointCheckService {

        private final String pingJson;
        private final String tcpJson;
        private final String httpJson;

        // Track which endpoints were actually called
        boolean pingCalled;
        boolean tcpCalled;
        boolean httpCalled;

        StubIsService(String pingJson, String tcpJson, String httpJson) {
            super(CONFIG);
            this.pingJson = pingJson;
            this.tcpJson  = tcpJson;
            this.httpJson  = httpJson;
        }

        @Override
        String callPingEndpoint(String host) throws IOException {
            pingCalled = true;
            if (pingJson == null) throw new IOException("IS unreachable");
            return pingJson;
        }

        @Override
        String callTcpEndpoint(String host, int port) throws IOException {
            tcpCalled = true;
            if (tcpJson == null) throw new IOException("IS unreachable");
            return tcpJson;
        }

        @Override
        String callHttpEndpoint(String url) throws IOException {
            httpCalled = true;
            if (httpJson == null) throw new IOException("IS unreachable");
            return httpJson;
        }
    }

    @Test
    void checkPingFail_tcpAndHttpStillCalled() {
        StubIsService svc = new StubIsService(
                "{\"reachable\":\"false\",\"response_time\":\"-1\"}",
                "{\"open\":\"true\",\"response_time\":\"8\"}",
                "{\"url\":\"https://host.example.com/path\","
                        + "\"http_status\":\"200\","
                        + "\"reachable\":\"true\","
                        + "\"error_msg\":\"\"}");

        EndpointCheckResult r = svc.check("API", "v1", "https://host.example.com/path");

        assertTrue(svc.pingCalled);
        assertTrue(svc.tcpCalled,  "TCP must still be called even when ping fails");
        assertTrue(svc.httpCalled, "HTTP must still be called even when ping fails");
        assertFalse(r.isPingOk());
        assertTrue(r.isTcpOk());
        assertEquals(200, r.getHttpStatus());
        assertTrue(r.isReachable());
    }

    @Test
    void checkTcpClosed_httpStillCalled() {
        StubIsService svc = new StubIsService(
                "{\"reachable\":\"true\",\"response_time\":\"5\"}",
                "{\"open\":\"false\",\"response_time\":\"-1\"}",
                "{\"url\":\"https://host.example.com/path\","
                        + "\"http_status\":\"200\","
                        + "\"reachable\":\"true\","
                        + "\"error_msg\":\"\"}");

        EndpointCheckResult r = svc.check("API", "v1", "https://host.example.com/path");

        assertTrue(svc.pingCalled);
        assertTrue(svc.tcpCalled);
        assertTrue(svc.httpCalled, "HTTP must still be called even when TCP is closed");
        assertTrue(r.isPingOk());
        assertFalse(r.isTcpOk());
        assertEquals(200, r.getHttpStatus());
        assertTrue(r.isReachable());
    }

    @Test
    void checkFullSuccess_allProbesCalled() {
        StubIsService svc = new StubIsService(
                "{\"reachable\":\"true\",\"response_time\":\"12\"}",
                "{\"open\":\"true\",\"response_time\":\"8\"}",
                "{\"url\":\"https://host.example.com/path\","
                        + "\"http_status\":\"200\","
                        + "\"reachable\":\"true\","
                        + "\"error_msg\":\"\"}");

        EndpointCheckResult r = svc.check("API", "v1", "https://host.example.com/path");

        assertTrue(svc.pingCalled);
        assertTrue(svc.tcpCalled);
        assertTrue(svc.httpCalled);
        assertTrue(r.isPingOk());
        assertEquals(12L, r.getPingMs());
        assertTrue(r.isTcpOk());
        assertEquals(8L, r.getTcpMs());
        assertEquals(200, r.getHttpStatus());
        assertTrue(r.isReachable());
        assertEquals("API", r.getApiName());
        assertEquals("v1",  r.getApiVersion());
    }

    @Test
    void checkAllProbesIoException_allCalledResultsInFail() {
        StubIsService svc = new StubIsService(null, null, null);

        EndpointCheckResult r = svc.check("API", "v1", "https://host.example.com/path");

        assertNotNull(r);
        assertTrue(svc.pingCalled);
        assertTrue(svc.tcpCalled,  "TCP must still be called even when ping throws IOException");
        assertTrue(svc.httpCalled, "HTTP must still be called even when TCP throws IOException");
        assertEquals(0, r.getHttpStatus());
        assertFalse(r.isReachable());
        assertTrue(r.getErrorMsg() != null && r.getErrorMsg().contains("IS probe unreachable"));
    }

    // -----------------------------------------------------------------------
    // check() — IS entirely unreachable (real closed port)
    // -----------------------------------------------------------------------

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
