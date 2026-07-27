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
            printUsage();
            return;
        }

        String command = args[0];
        Path kdbxFile = null;
        String kdbxPassword = null;

        // --kdbx <datei> --kdbx-password <passwort> parsen
        for (int i = 1; i < args.length - 1; i++) {
            if ("--kdbx".equals(args[i])) {
                kdbxFile = Paths.get(args[i + 1]);
            } else if ("--kdbx-password".equals(args[i])) {
                kdbxPassword = args[i + 1];
            }
        }

        // Validierung: --kdbx ohne --kdbx-password
        if (kdbxFile != null && kdbxPassword == null) {
            System.err.println("Fehler: --kdbx erfordert auch --kdbx-password.");
            printUsage();
            return;
        }

        switch (command) {
            case "ping":
                runPing(kdbxFile, kdbxPassword);
                break;
            case "tcp":
                runTcpCheck(kdbxFile, kdbxPassword);
                break;
            default:
                System.out.println("Unbekannter Befehl: " + command);
                printUsage();
        }
    }

    static void runPing(Path kdbxFile, String kdbxPassword) {
        List<ServerConfig> servers = loadServers(kdbxFile, kdbxPassword);
        if (servers == null) return;

        if (servers.isEmpty()) {
            System.out.println("Keine Server konfiguriert.");
            return;
        }

        PingService pingService = new PingService();
        List<PingResult> results = new ArrayList<>();
        for (ServerConfig server : servers) {
            results.add(pingService.ping(server));
        }

        System.out.println(new PingResultFormatter().format(results));
    }

    // Rückwärtskompatibles Overload für Tests mit properties-Datei
    static void runPing(Path configFile) {
        List<ServerConfig> servers = loadFromProperties(configFile);
        if (servers == null) return;

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

    static void runTcpCheck(Path kdbxFile, String kdbxPassword) {
        List<ServerConfig> servers = loadServers(kdbxFile, kdbxPassword);
        if (servers == null) return;

        if (servers.isEmpty()) {
            System.out.println("Keine Server konfiguriert.");
            return;
        }

        TcpCheckService tcpService = new TcpCheckService();
        List<TcpCheckResult> results = new ArrayList<>();
        for (ServerConfig server : servers) {
            results.add(tcpService.check(server));
        }

        System.out.println(new TcpCheckResultFormatter().format(results));
    }

    // Rückwärtskompatibles Overload für Tests mit properties-Datei
    static void runTcpCheck(Path configFile) {
        List<ServerConfig> servers = loadFromProperties(configFile);
        if (servers == null) return;

        if (servers.isEmpty()) {
            System.out.println("Keine Server in " + configFile + " konfiguriert.");
            return;
        }

        TcpCheckService tcpService = new TcpCheckService();
        List<TcpCheckResult> results = new ArrayList<>();
        for (ServerConfig server : servers) {
            results.add(tcpService.check(server));
        }

        System.out.println(new TcpCheckResultFormatter().format(results));
    }

    // Lädt Server: KeePass wenn kdbxFile gesetzt, sonst servers.properties
    private static List<ServerConfig> loadServers(Path kdbxFile, String kdbxPassword) {
        if (kdbxFile != null) {
            try {
                return new KeePassConfigLoader().load(kdbxFile, kdbxPassword);
            } catch (IOException e) {
                System.err.println("Fehler: KeePass-Datei " + kdbxFile + " konnte nicht geladen werden: " + e.getMessage());
                return null;
            }
        }
        return loadFromProperties(Paths.get(CONFIG_FILE));
    }

    private static List<ServerConfig> loadFromProperties(Path configFile) {
        try {
            return new ConfigLoader().load(configFile);
        } catch (IOException e) {
            System.err.println("Fehler: " + configFile + " nicht gefunden oder lesbar: " + e.getMessage());
            return null;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: agwcontrol <ping|tcp> [--kdbx <datei> --kdbx-password <passwort>]");
    }
}
