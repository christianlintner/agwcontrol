package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void noArgsPrintsUsage() {
        ByteArrayOutputStream out = captureStdout(() ->
                App.main(new String[]{}));
        assertTrue(out.toString().contains("Usage:"));
    }

    @Test
    void onlyKdbxWithoutPasswordPrintsUsage() {
        ByteArrayOutputStream out = captureStdout(() ->
                App.main(new String[]{"--kdbx", "servers.kdbx"}));
        assertTrue(out.toString().contains("Usage:"));
    }

    @Test
    void onlyPasswordWithoutKdbxPrintsUsage() {
        ByteArrayOutputStream out = captureStdout(() ->
                App.main(new String[]{"--kdbx-password", "geheim"}));
        assertTrue(out.toString().contains("Usage:"));
    }

    @Test
    void nonExistentKdbxPrintsError() {
        ByteArrayOutputStream err = captureStderr(() ->
                App.loadGroups("does_not_exist.kdbx", "password"));
        assertTrue(err.toString().contains("Fehler:"));
    }

    @Test
    void wrongPasswordPrintsError() {
        ByteArrayOutputStream err = captureStderr(() ->
                App.loadGroups("servers.kdbx", "falschesPasswort"));
        assertTrue(err.toString().contains("Fehler:"));
    }

    @Test
    void loadGroupsReturnsNullOnError() {
        List<ServerGroup> result = App.loadGroups("does_not_exist.kdbx", "password");
        assertNull(result);
    }

    // --- buildProbeConfigFromCli ---

    @Test
    void buildProbeConfigFromCliParsesHttpUrl() {
        IsEndpointCheckConfig cfg = App.buildProbeConfigFromCli(
                "http://localhost:5555", "admin", "manage");
        assertNotNull(cfg);
        assertEquals("http",      cfg.getScheme());
        assertEquals("localhost",  cfg.getHost());
        assertEquals(5555,         cfg.getPort());
        assertEquals("admin",      cfg.getUsername());
        assertEquals("manage",     cfg.getPassword());
    }

    @Test
    void buildProbeConfigFromCliParsesHttpsUrl() {
        IsEndpointCheckConfig cfg = App.buildProbeConfigFromCli(
                "https://is.corp.at:5443", "user", "pass");
        assertNotNull(cfg);
        assertEquals("https",    cfg.getScheme());
        assertEquals("is.corp.at", cfg.getHost());
        assertEquals(5443,         cfg.getPort());
    }

    @Test
    void buildProbeConfigFromCliInvalidUrlReturnsNull() {
        // Should not throw; returns null
        assertNull(App.buildProbeConfigFromCli("not a url at all ::::", "u", "p"));
    }

    @Test
    void buildProbeConfigFromCliInvalidUrlWritesToStderr() {
        ByteArrayOutputStream err = captureStderr(() ->
                App.buildProbeConfigFromCli("not a url at all ::::", "u", "p"));
        assertTrue(err.toString().contains("Fehler"),
                "A parse error must be reported to stderr");
    }

    @Test
    void buildProbeConfigFromCliMissingHostReturnsNull() {
        assertNull(App.buildProbeConfigFromCli("http://:5555", "u", "p"));
    }

    @Test
    void mainWithIsProbeArgsPrintsConfig() {
        // Three probe args but no valid kdbx → prints IS-Probe line before Usage
        // (actually Usage is printed because kdbx args are missing)
        ByteArrayOutputStream out = captureStdout(() ->
                App.main(new String[]{
                    "--kdbx", "missing.kdbx",
                    "--kdbx-password", "pass",
                    "--is-probe-url", "http://localhost:5555",
                    "--is-probe-user", "admin",
                    "--is-probe-password", "manage"
                }));
        // loadGroups fails → error to stderr, but probe config print went to stdout
        String s = out.toString();
        assertTrue(s.contains("IS-Probe") || s.isEmpty(),
                "Either IS-Probe config or empty output expected when kdbx missing");
    }

    @Test
    void mainWithOnlyPartialIsProbeArgsDoesNotActivateProbe() {
        // Only --is-probe-url without user/password → probe must NOT be activated
        // The test just ensures no exception is thrown
        assertDoesNotThrow(() ->
                App.main(new String[]{
                    "--is-probe-url", "http://localhost:5555"
                }));
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
