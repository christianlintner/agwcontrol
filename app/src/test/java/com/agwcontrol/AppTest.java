package com.agwcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    @Test
    void kdbxPingFlowLoadsServers() throws URISyntaxException {
        Path kdbx = testKdbx();
        ByteArrayOutputStream out = captureStdout(() ->
                App.runPing(kdbx, "test1234"));

        String output = out.toString();
        assertTrue(output.contains("vm40757.linux.oebb.at"), "Host aus KeePass muss in der Ausgabe stehen");
    }

    @Test
    void kdbxTcpFlowLoadsServers() throws URISyntaxException {
        Path kdbx = testKdbx();
        ByteArrayOutputStream out = captureStdout(() ->
                App.runTcpCheck(kdbx, "test1234"));

        // Alle Einträge werden gecheckt – Ausgabe muss mindestens einen Host enthalten
        assertTrue(out.toString().contains("vm40757.linux.oebb.at"), "Host aus KeePass muss in der Ausgabe stehen");
    }

    @Test
    void kdbxMissingPasswordPrintsError() {
        ByteArrayOutputStream err = captureStderr(() ->
                App.main(new String[]{"ping", "--kdbx", "servers.kdbx"}));

        assertTrue(err.toString().contains("Fehler:"));
    }

    @Test
    void kdbxWrongPasswordPrintsError() throws URISyntaxException {
        Path kdbx = testKdbx();
        ByteArrayOutputStream err = captureStderr(() ->
                App.runPing(kdbx, "falschesPasswort"));

        assertTrue(err.toString().contains("Fehler:"));
    }

    // --- Hilfsmethoden ---

    private Path testKdbx() throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("test.kdbx");
        assertNotNull(resource, "test.kdbx nicht in src/test/resources gefunden");
        return Paths.get(resource.toURI());
    }

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
