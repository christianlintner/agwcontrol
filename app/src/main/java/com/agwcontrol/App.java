package com.agwcontrol;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class App {

    public static void main(String[] args) {
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
    }
}
