package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class EndpointCheckServiceTest {

    private final EndpointCheckService service = new EndpointCheckService();

    /**
     * Startet einen Multi-Accept-Server, der auf jede eingehende Verbindung dieselbe
     * HTTP-Response zurückliefert. Schliesst sich automatisch nach dem ersten echten
     * HTTP-Request (erkennbar an "HTTP/" in der Anfrage).
     */
    private int startOneShotServer(String response) throws IOException {
        ServerSocket ss = new ServerSocket(0);
        ExecutorService ex = Executors.newCachedThreadPool();
        ex.submit(() -> {
            while (!ss.isClosed()) {
                try {
                    Socket s = ss.accept();
                    ex.submit(() -> {
                        try {
                            byte[] buf = new byte[4096];
                            int n = s.getInputStream().read(buf);
                            String req = n > 0 ? new String(buf, 0, n) : "";
                            OutputStream out = s.getOutputStream();
                            PrintWriter pw = new PrintWriter(out, true);
                            pw.print(response);
                            pw.flush();
                            s.close();
                            // Nach echtem HTTP-Request Server schliessen
                            if (req.contains("HTTP/")) {
                                ss.close();
                            }
                        } catch (IOException ignored) {}
                    });
                } catch (IOException ignored) {
                    break;
                }
            }
            ex.shutdown();
        });
        return ss.getLocalPort();
    }

    @Test
    void reachableOn200() throws Exception {
        int port = startOneShotServer(
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        String url = "http://127.0.0.1:" + port + "/test";
        EndpointCheckResult r = service.check("MyAPI", "v1", url);
        assertEquals(200, r.getHttpStatus());
        assertTrue(r.isReachable());
        assertEquals("MyAPI", r.getApiName());
        assertEquals("v1", r.getApiVersion());
    }

    @Test
    void reachableOn404() throws Exception {
        // 404 = Server antwortet → erreichbar (nur Timeout/Verbindungsfehler = FAIL)
        int port = startOneShotServer(
                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        String url = "http://127.0.0.1:" + port + "/nope";
        EndpointCheckResult r = service.check("OldAPI", "2.0", url);
        assertEquals(404, r.getHttpStatus());
        assertTrue(r.isReachable());
    }

    @Test
    void reachableOn500() throws Exception {
        // 500 = Server antwortet → erreichbar
        int port = startOneShotServer(
                "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        String url = "http://127.0.0.1:" + port + "/err";
        EndpointCheckResult r = service.check("ErrAPI", "1.0", url);
        assertEquals(500, r.getHttpStatus());
        assertTrue(r.isReachable());
    }

    @Test
    void unreachableHostReturnsStatus0() {
        // Port 1 ist auf localhost nicht erreichbar
        EndpointCheckResult r = service.check("API", "1", "http://127.0.0.1:1/ep");
        assertEquals(0, r.getHttpStatus());
        assertFalse(r.isReachable());
        assertNotNull(r.getErrorMsg());
        assertFalse(r.getErrorMsg().isEmpty());
    }

    @Test
    void apiNameAndVersionPreservedOnError() {
        EndpointCheckResult r = service.check("FailAPI", "v9", "http://127.0.0.1:1/ep");
        assertEquals("FailAPI", r.getApiName());
        assertEquals("v9", r.getApiVersion());
    }

    @Test
    void urlPreservedInResult() throws Exception {
        int port = startOneShotServer(
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        String url = "http://127.0.0.1:" + port + "/my/path";
        EndpointCheckResult r = service.check("X", "1", url);
        assertEquals(url, r.getUrl());
    }

    @Test
    void pingOkForLoopback() throws Exception {
        int port = startOneShotServer(
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        String url = "http://127.0.0.1:" + port + "/test";
        EndpointCheckResult r = service.check("A", "1", url);
        assertTrue(r.isPingOk(), "Ping auf 127.0.0.1 muss OK sein");
        assertTrue(r.getPingMs() >= 0);
    }

    @Test
    void tcpOkWhenPortOpen() throws Exception {
        int port = startOneShotServer(
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        String url = "http://127.0.0.1:" + port + "/test";
        EndpointCheckResult r = service.check("A", "1", url);
        assertTrue(r.isTcpOk(), "TCP auf offenem Port muss OK sein");
        assertTrue(r.getTcpMs() >= 0);
    }

    @Test
    void pingAndTcpFailForUnreachablePort() {
        EndpointCheckResult r = service.check("A", "1", "http://127.0.0.1:1/ep");
        assertFalse(r.isTcpOk());
        assertEquals(-1, r.getTcpMs());
    }
}
