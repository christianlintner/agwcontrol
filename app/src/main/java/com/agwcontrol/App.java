package com.agwcontrol;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class App {

    static final String CONFIG_FILE = "servers.properties";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: agwcontrol <ping>");
            return;
        }

        switch (args[0]) {
            case "ping":
                runPing(Paths.get(CONFIG_FILE));
                break;
            default:
                System.out.println("Unbekannter Befehl: " + args[0]);
                System.out.println("Usage: agwcontrol <ping>");
        }
    }

    static void runPing(Path configFile) {
        List<ServerConfig> servers;
        try {
            servers = new ConfigLoader().load(configFile);
        } catch (IOException e) {
            System.err.println("Fehler: " + configFile + " nicht gefunden oder lesbar: " + e.getMessage());
            return;
        }

        if (servers.isEmpty()) {
            System.out.println("Keine Server in " + configFile + " konfiguriert.");
            return;
        }

        PingService pingService = new PingService();
        List<PingResult> results = new ArrayList<>();
        for (ServerConfig server : servers) {
            results.add(pingService.ping(server));
        }

        System.out.println(new PingResultFormatter().format(results));
    }
}
