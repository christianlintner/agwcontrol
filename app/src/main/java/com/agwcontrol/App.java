package com.agwcontrol;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

public class App {

    public static void main(String[] args) {
        // Subkommando "report" abfangen
        if (args.length > 0 && "report".equals(args[0])) {
            runReport(args);
            return;
        }

        String kdbxPath = null;
        String kdbxPassword = null;

        for (int i = 0; i < args.length - 1; i++) {
            if ("--kdbx".equals(args[i])) {
                kdbxPath = args[i + 1];
            } else if ("--kdbx-password".equals(args[i])) {
                kdbxPassword = args[i + 1];
            }
        }

        if (kdbxPath == null || kdbxPassword == null) {
            printUsage();
            return;
        }

        List<ServerGroup> groups = loadGroups(kdbxPath, kdbxPassword);
        if (groups == null) return;

        new InteractiveMenu(groups, System.in, System.out).run();
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

    private static void printUsage() {
        System.out.println("Usage: agwcontrol --kdbx <datei> --kdbx-password <passwort>");
        System.out.println("       agwcontrol report [--db-path <datei>] [--output-dir <verzeichnis>]");
    }
}
