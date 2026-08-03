package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.List;

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

    @Test
    void checkByHostPortOpenPort() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            TcpCheckResult result = new TcpCheckService().check("127.0.0.1", port, 2000);
            assertTrue(result.isOpen());
            assertTrue(result.getResponseTimeMs() >= 0);
            assertEquals("127.0.0.1", result.getHost());
            assertEquals(port, result.getPort());
        }
    }

    @Test
    void checkByHostPortClosedPort() {
        TcpCheckResult result = new TcpCheckService().check("127.0.0.1", 1, 500);
        assertFalse(result.isOpen());
        assertEquals(-1, result.getResponseTimeMs());
    }

    @Test
    void checkAllIncludesAgwOnly() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            ServerConfig server = new ServerConfig("127.0.0.1", port);
            List<TcpCheckResult> results = new TcpCheckService().checkAll(server);

            assertEquals(1, results.size());
            assertEquals("AGW", results.get(0).getLabel());
            assertEquals("127.0.0.1", results.get(0).getHost());
        }
    }

    @Test
    void checkAllIncludesAllUrls() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            String url = "http://127.0.0.1:" + port;
            ServerConfig server = new ServerConfig(
                    "127.0.0.1", port,
                    null, null,
                    url, url, url
            );
            List<TcpCheckResult> results = new TcpCheckService().checkAll(server);

            assertEquals(4, results.size());
            assertEquals("AGW",          results.get(0).getLabel());
            assertEquals("IS",           results.get(1).getLabel());
            assertEquals("CLUSTER",      results.get(2).getLabel());
            assertEquals("CLUSTER-CERT", results.get(3).getLabel());
        }
    }

    @Test
    void checkAllSkipsMissingUrls() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            String url = "http://127.0.0.1:" + port;
            ServerConfig server = new ServerConfig(
                    "127.0.0.1", port,
                    null, null,
                    null, url, null
            );
            List<TcpCheckResult> results = new TcpCheckService().checkAll(server);

            assertEquals(2, results.size());
            assertEquals("AGW",     results.get(0).getLabel());
            assertEquals("CLUSTER", results.get(1).getLabel());
        }
    }

    @Test
    void hostFromUrlExtractsHost() {
        assertEquals("apigateway-oh-preprod.oebb.at",
                TcpCheckService.hostFromUrl("https://apigateway-oh-preprod.oebb.at"));
    }

    @Test
    void portFromUrlExtractsPort() {
        assertEquals(8080, TcpCheckService.portFromUrl("https://host.example.com:8080/path"));
    }

    @Test
    void portFromUrlDefaultsTo443() {
        assertEquals(443, TcpCheckService.portFromUrl("https://host.example.com/path"));
    }
}
