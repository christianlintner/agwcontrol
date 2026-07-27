package com.agwcontrol;

import org.junit.jupiter.api.Test;

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
}
