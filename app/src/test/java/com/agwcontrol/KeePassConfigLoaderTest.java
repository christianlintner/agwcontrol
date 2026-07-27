package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeePassConfigLoaderTest {

    private static final String MASTER_PASSWORD = "test1234";

    private Path testKdbx() throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("test.kdbx");
        assertNotNull(resource, "test.kdbx nicht in src/test/resources gefunden");
        return Paths.get(resource.toURI());
    }

    @Test
    void loadsAllEntries() throws Exception {
        List<ServerConfig> configs = new KeePassConfigLoader().load(testKdbx(), MASTER_PASSWORD);
        assertEquals(10, configs.size());
    }

    @Test
    void parsesHostAndPort() throws Exception {
        List<ServerConfig> configs = new KeePassConfigLoader().load(testKdbx(), MASTER_PASSWORD);

        ServerConfig first = configs.stream()
                .filter(c -> c.getHost().equals("vm40757.linux.oebb.at"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eintrag vm40757.linux.oebb.at nicht gefunden"));

        assertEquals("vm40757.linux.oebb.at", first.getHost());
        assertEquals(443, first.getPort());
    }

    @Test
    void parsesUsernameAndPassword() throws Exception {
        List<ServerConfig> configs = new KeePassConfigLoader().load(testKdbx(), MASTER_PASSWORD);

        ServerConfig entry = configs.stream()
                .filter(c -> c.getHost().equals("vm40757.linux.oebb.at"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eintrag vm40757.linux.oebb.at nicht gefunden"));

        assertEquals("agwuser", entry.getUsername());
        assertEquals("agwpassword1", entry.getPassword());
    }

    @Test
    void throwsOnWrongPassword() {
        assertThrows(IOException.class,
                () -> new KeePassConfigLoader().load(testKdbx(), "falschesPasswort"));
    }
}
