package com.agwcontrol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

public class ConfigLoader {

    public List<ServerConfig> load(Path file) throws IOException {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(file)) {
            props.load(is);
        }

        // Sammle alle Index-Nummern (server.N.host)
        TreeMap<Integer, String> hosts = new TreeMap<>();
        TreeMap<Integer, Integer> ports = new TreeMap<>();

        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("server.") && key.endsWith(".host")) {
                int idx = parseIndex(key);
                if (idx >= 0) {
                    hosts.put(idx, props.getProperty(key).trim());
                }
            } else if (key.startsWith("server.") && key.endsWith(".port")) {
                int idx = parseIndex(key);
                if (idx >= 0) {
                    String raw = props.getProperty(key).trim();
                    try {
                        ports.put(idx, Integer.parseInt(raw));
                    } catch (NumberFormatException e) {
                        throw new IOException("Ungültiger Port-Wert für server." + idx + ".port: " + raw);
                    }
                }
            }
        }

        List<ServerConfig> result = new ArrayList<>();
        for (int idx : hosts.keySet()) {
            String host = hosts.get(idx);
            int port = ports.getOrDefault(idx, 443);
            result.add(new ServerConfig(host, port));
        }
        return result;
    }

    private int parseIndex(String key) {
        // Format: server.<N>.(host|port)
        String[] parts = key.split("\\.");
        if (parts.length != 3) return -1;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
