package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PingServiceTest {

    @Test
    void loopbackIsReachable() {
        ServerConfig loopback = new ServerConfig("127.0.0.1", 0);
        PingResult result = new PingService().ping(loopback);

        assertEquals("127.0.0.1", result.getHost());
        assertTrue(result.isReachable(), "127.0.0.1 muss erreichbar sein");
        assertTrue(result.getResponseTimeMs() >= 0, "Antwortzeit muss >= 0 sein");
    }

    @Test
    void unreachableHostReturnsUnreachable() {
        // RFC 5737 TEST-NET — darf im Netz nie geroutet werden
        ServerConfig unreachable = new ServerConfig("192.0.2.1", 0);
        PingResult result = new PingService().ping(unreachable, 500);

        assertFalse(result.isReachable());
        assertEquals(-1, result.getResponseTimeMs());
    }

    @Test
    void pingByHostStringLoopbackReachable() {
        PingResult result = new PingService().ping("127.0.0.1", 2000);
        assertEquals("127.0.0.1", result.getHost());
        assertTrue(result.isReachable());
        assertTrue(result.getResponseTimeMs() >= 0);
    }

    @Test
    void pingByHostStringUnreachable() {
        PingResult result = new PingService().ping("192.0.2.1", 500);
        assertFalse(result.isReachable());
        assertEquals(-1, result.getResponseTimeMs());
    }

    @Test
    void pingAllIncludesAgwOnly() {
        ServerConfig server = new ServerConfig("127.0.0.1", 443);
        List<PingResult> results = new PingService().pingAll(server);

        assertEquals(1, results.size());
        assertEquals("AGW", results.get(0).getLabel());
        assertEquals("127.0.0.1", results.get(0).getHost());
    }

    @Test
    void pingAllIncludesAllUrls() {
        ServerConfig server = new ServerConfig(
                "127.0.0.1", 443,
                null, null,
                "https://127.0.0.1:443",
                "https://127.0.0.1:443",
                "https://127.0.0.1:443"
        );
        List<PingResult> results = new PingService().pingAll(server);

        assertEquals(4, results.size());
        assertEquals("AGW",          results.get(0).getLabel());
        assertEquals("IS",           results.get(1).getLabel());
        assertEquals("CLUSTER",      results.get(2).getLabel());
        assertEquals("CLUSTER-CERT", results.get(3).getLabel());
    }

    @Test
    void pingAllSkipsMissingUrls() {
        ServerConfig server = new ServerConfig(
                "127.0.0.1", 443,
                null, null,
                null,
                "https://127.0.0.1:443",
                null
        );
        List<PingResult> results = new PingService().pingAll(server);

        assertEquals(2, results.size());
        assertEquals("AGW",     results.get(0).getLabel());
        assertEquals("CLUSTER", results.get(1).getLabel());
    }

    @Test
    void hostFromUrlExtractsHost() {
        assertEquals("apigateway-oh-preprod.oebb.at",
                PingService.hostFromUrl("https://apigateway-oh-preprod.oebb.at"));
    }

    @Test
    void hostFromUrlReturnsNullOnInvalid() {
        assertNull(PingService.hostFromUrl("not a url :::"));
    }
}
