package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

class TcpCheckServiceTest {

    @Test
    void openPort() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            ServerConfig server = new ServerConfig("127.0.0.1", port);
            TcpCheckResult result = new TcpCheckService().check(server);
            assertTrue(result.isOpen());
            assertTrue(result.getResponseTimeMs() >= 0);
            assertEquals("127.0.0.1", result.getHost());
            assertEquals(port, result.getPort());
        }
    }

    @Test
    void closedPort() {
        // Port 1 ist unter normalen Umständen nicht erreichbar
        ServerConfig server = new ServerConfig("127.0.0.1", 1);
        TcpCheckResult result = new TcpCheckService().check(server, 500);
        assertFalse(result.isOpen());
        assertEquals(-1, result.getResponseTimeMs());
    }
}
