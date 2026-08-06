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
