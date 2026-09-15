package com.agwcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

    // --- buildApiFilterSet ---

    @Test
    void buildApiFilterSetEmptyWhenNullArgs() throws IOException {
        Set<String> result = App.buildApiFilterSet(null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildApiFilterSetFromApiFilterString() throws IOException {
        Set<String> result = App.buildApiFilterSet("Api1,Api2, Api3 ", null);
        assertEquals(Set.of("api1", "api2", "api3"), result);
    }

    @Test
    void buildApiFilterSetIsCaseInsensitive() throws IOException {
        Set<String> result = App.buildApiFilterSet("MyAPI,ANOTHER", null);
        assertTrue(result.contains("myapi"));
        assertTrue(result.contains("another"));
    }

    @Test
    void buildApiFilterSetFromFile(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("apis.txt");
        Files.writeString(file, "ApiA\n# Kommentar\n\nApiB\n  ApiC  \n");
        Set<String> result = App.buildApiFilterSet(null, file.toString());
        assertEquals(Set.of("apia", "apib", "apic"), result);
    }

    @Test
    void buildApiFilterSetFromFileSkipsComments(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("apis.txt");
        Files.writeString(file, "# This is a comment\nGoodApi\n");
        Set<String> result = App.buildApiFilterSet(null, file.toString());
        assertEquals(Set.of("goodapi"), result);
    }

    @Test
    void buildApiFilterSetCombinesBothSources(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("apis.txt");
        Files.writeString(file, "FromFile\n");
        Set<String> result = App.buildApiFilterSet("FromArg", file.toString());
        assertTrue(result.contains("fromarg"));
        assertTrue(result.contains("fromfile"));
    }

    @Test
    void buildApiFilterSetThrowsOnMissingFile() {
        assertThrows(IOException.class,
                () -> App.buildApiFilterSet(null, "/nonexistent/path/apis.txt"));
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
