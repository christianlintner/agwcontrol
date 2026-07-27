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

    /** Startet einen einmaligen HTTP-Server, der exakt `response` zurückliefert. */
    private int startOneShotServer(String response) throws IOException {
        ServerSocket ss = new ServerSocket(0);
        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.submit(() -> {
            try (Socket s = ss.accept()) {
                // Request lesen (und ignorieren)
                byte[] buf = new byte[4096];
                s.getInputStream().read(buf);
                OutputStream out = s.getOutputStream();
                PrintWriter pw = new PrintWriter(out, true);
                pw.print(response);
                pw.flush();
            } catch (IOException ignored) {
            } finally {
                try { ss.close(); } catch (IOException ignored) {}
                ex.shutdown();
            }
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
    void notReachableOn404() throws Exception {
        int port = startOneShotServer(
                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        String url = "http://127.0.0.1:" + port + "/nope";
        EndpointCheckResult r = service.check("OldAPI", "2.0", url);
        assertEquals(404, r.getHttpStatus());
        assertFalse(r.isReachable());
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
}
