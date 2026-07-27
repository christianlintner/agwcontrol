package com.agwcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @TempDir
    Path tempDir;

    @Test
    void unknownCommandPrintsUsage() {
        ByteArrayOutputStream out = captureStdout(() ->
                App.main(new String[]{"unknown"}));

        assertTrue(out.toString().contains("Usage:"));
    }

    @Test
    void noArgsPrintsUsage() {
        ByteArrayOutputStream out = captureStdout(() ->
                App.main(new String[]{}));

        assertTrue(out.toString().contains("Usage:"));
    }

    @Test
    void pingFlowWithLoopback() throws IOException {
        Path props = tempDir.resolve("servers.properties");
        Files.writeString(props, "server.1.host=127.0.0.1\nserver.1.port=0\n");

        ByteArrayOutputStream out = captureStdout(() ->
                App.runPing(props));

        String output = out.toString();
        assertTrue(output.contains("127.0.0.1"), "Host muss in der Ausgabe stehen");
        assertTrue(output.contains("OK"), "Loopback muss erreichbar sein");
    }

    @Test
    void pingFlowMissingConfigPrintsError() {
        Path missing = tempDir.resolve("missing.properties");
        ByteArrayOutputStream err = captureStderr(() ->
                App.runPing(missing));

        assertTrue(err.toString().contains("Fehler:"));
    }

    @Test
    void tcpFlowWithLoopback() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Path props = tempDir.resolve("servers-tcp.properties");
            Files.writeString(props, "server.1.host=127.0.0.1\nserver.1.port=" + port + "\n");

            ByteArrayOutputStream out = captureStdout(() ->
                    App.runTcpCheck(props));

            String output = out.toString();
            assertTrue(output.contains("127.0.0.1:" + port), "Host:Port muss in der Ausgabe stehen");
            assertTrue(output.contains("OPEN"), "Offener Port muss als OPEN erkannt werden");
        }
    }

    @Test
    void tcpFlowMissingConfigPrintsError() {
        Path missing = tempDir.resolve("missing-tcp.properties");
        ByteArrayOutputStream err = captureStderr(() ->
                App.runTcpCheck(missing));

        assertTrue(err.toString().contains("Fehler:"));
    }

    // --- Hilfsmethoden ---

    private ByteArrayOutputStream captureStdout(Runnable action) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(buf));
        try {
            action.run();
        } finally {
            System.setOut(old);
        }
        return buf;
    }

    private ByteArrayOutputStream captureStderr(Runnable action) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream old = System.err;
        System.setErr(new PrintStream(buf));
        try {
            action.run();
        } finally {
            System.setErr(old);
        }
        return buf;
    }
}
