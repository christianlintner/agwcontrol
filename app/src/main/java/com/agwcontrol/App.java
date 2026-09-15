package com.agwcontrol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class App {

    public static void main(String[] args) {
        // Subkommando "report" abfangen
        if (args.length > 0 && "report".equals(args[0])) {
            runReport(args);
            return;
        }

        String kdbxPath     = null;
        String kdbxPassword = null;
        String apiFilter    = null;
        String apiListFile  = null;

        for (int i = 0; i < args.length - 1; i++) {
            if ("--kdbx".equals(args[i])) {
                kdbxPath = args[i + 1];
            } else if ("--kdbx-password".equals(args[i])) {
                kdbxPassword = args[i + 1];
            } else if ("--api-filter".equals(args[i])) {
                apiFilter = args[i + 1];
            } else if ("--api-list-file".equals(args[i])) {
                apiListFile = args[i + 1];
            }
        }

        if (kdbxPath == null || kdbxPassword == null) {
            printUsage();
            return;
        }

        Set<String> apiFilterNames;
        try {
            apiFilterNames = buildApiFilterSet(apiFilter, apiListFile);
        } catch (IOException e) {
            System.err.println("Fehler: API-Filterdatei konnte nicht gelesen werden: " + e.getMessage());
            return;
        }

        List<ServerGroup> groups = loadGroups(kdbxPath, kdbxPassword);
        if (groups == null) return;

        new InteractiveMenu(groups, System.in, System.out, "agwcontrol.db", apiFilterNames).run();
    }

    static void runReport(String[] args) {
        String dbPath    = "agwcontrol.db";
        String outputDir = ".";

        for (int i = 1; i < args.length - 1; i++) {
            if ("--db-path".equals(args[i])) {
                dbPath = args[i + 1];
            } else if ("--output-dir".equals(args[i])) {
                outputDir = args[i + 1];
            }
        }

        ApiDatabase db = new ApiDatabase(dbPath);
        try {
            db.initSchema();
            int created = new DbReportService(db).writeReports(Path.of(outputDir));
            if (created == 0) {
                System.out.println("Keine Daten in der Datenbank.");
            }
        } catch (SQLException | IOException e) {
            System.err.println("Fehler beim Report: " + e.getMessage());
        }
    }

    static List<ServerGroup> loadGroups(String kdbxPath, String kdbxPassword) {
        try {
            return new KeePassConfigLoader().loadGroups(Paths.get(kdbxPath), kdbxPassword);
        } catch (IOException e) {
            System.err.println("Fehler: KeePass-Datei " + kdbxPath + " konnte nicht geladen werden: " + e.getMessage());
            return null;
        }
    }

    static Set<String> buildApiFilterSet(String apiFilter, String apiListFile) throws IOException {
        Set<String> result = new HashSet<>();
        if (apiFilter != null) {
            for (String token : apiFilter.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed.toLowerCase());
                }
            }
        }
        if (apiListFile != null) {
            for (String line : Files.readAllLines(Paths.get(apiListFile), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    result.add(trimmed.toLowerCase());
                }
            }
        }
        return result;
    }

    private static void printUsage() {
        System.out.println("Usage: agwcontrol --kdbx <datei> --kdbx-password <passwort>");
        System.out.println("                  [--api-list-file <datei>] [--api-filter <api1,api2,...>]");
        System.out.println("       agwcontrol report [--db-path <datei>] [--output-dir <verzeichnis>]");
    }
}
