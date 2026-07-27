package com.agwcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsMultipleServers() throws IOException {
        Path props = tempDir.resolve("servers.properties");
        Files.writeString(props,
                "server.1.host=agw-server-1.example.com\n" +
                "server.1.port=8443\n" +
                "server.2.host=agw-server-2.example.com\n" +
                "server.2.port=9443\n");

        List<ServerConfig> configs = new ConfigLoader().load(props);

        assertEquals(2, configs.size());
        assertEquals("agw-server-1.example.com", configs.get(0).getHost());
        assertEquals(8443, configs.get(0).getPort());
        assertEquals("agw-server-2.example.com", configs.get(1).getHost());
        assertEquals(9443, configs.get(1).getPort());
    }

    @Test
    void defaultsPortTo443WhenMissing() throws IOException {
        Path props = tempDir.resolve("servers.properties");
        Files.writeString(props, "server.1.host=agw-server-1.example.com\n");

        List<ServerConfig> configs = new ConfigLoader().load(props);

        assertEquals(1, configs.size());
        assertEquals(443, configs.get(0).getPort());
    }

    @Test
    void throwsWhenFileNotFound() {
        Path missing = tempDir.resolve("missing.properties");
        assertThrows(IOException.class, () -> new ConfigLoader().load(missing));
    }
}
