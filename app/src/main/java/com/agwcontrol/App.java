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

        String kdbxPath     = null;
        String kdbxPassword = null;
        // IS probe CLI args — override KeePass custom fields for all servers
        String isProbeUrl      = null;
        String isProbeUser     = null;
        String isProbePassword = null;

        for (int i = 0; i < args.length - 1; i++) {
            if ("--kdbx".equals(args[i])) {
                kdbxPath = args[i + 1];
            } else if ("--kdbx-password".equals(args[i])) {
                kdbxPassword = args[i + 1];
            } else if ("--is-probe-url".equals(args[i])) {
                isProbeUrl = args[i + 1];
            } else if ("--is-probe-user".equals(args[i])) {
                isProbeUser = args[i + 1];
            } else if ("--is-probe-password".equals(args[i])) {
                isProbePassword = args[i + 1];
            }
        }

        if (kdbxPath == null || kdbxPassword == null) {
            printUsage();
            return;
        }

        List<ServerGroup> groups = loadGroups(kdbxPath, kdbxPassword);
        if (groups == null) return;

        InteractiveMenu menu = new InteractiveMenu(groups, System.in, System.out);

        // Apply CLI IS probe override if all three args are supplied
        if (isProbeUrl != null && isProbeUser != null && isProbePassword != null) {
            IsEndpointCheckConfig probeConfig = buildProbeConfigFromCli(
                    isProbeUrl, isProbeUser, isProbePassword);
            if (probeConfig != null) {
                menu.setGlobalIsProbeConfig(probeConfig);
                System.out.println("IS-Probe konfiguriert: " + probeConfig);
            }
        }

        menu.run();
    }

    /**
     * Parses the IS probe URL from the CLI and builds an {@link IsEndpointCheckConfig}.
     * The URL must include scheme and port, e.g. {@code http://localhost:5555}.
     */
    static IsEndpointCheckConfig buildProbeConfigFromCli(String url, String user, String password) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host   = uri.getHost();
            int    port   = uri.getPort() > 0 ? uri.getPort() : IsEndpointCheckConfig.DEFAULT_PORT_HTTP;
            if (host == null || host.isEmpty()) {
                System.err.println("Fehler: --is-probe-url \"" + url + "\" enthält keinen Hostnamen.");
                return null;
            }
            return new IsEndpointCheckConfig(scheme, host, port, user, password);
        } catch (java.net.URISyntaxException e) {
            System.err.println("Fehler: --is-probe-url \"" + url + "\" ist keine gültige URL: " + e.getMessage());
            return null;
        }
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
        System.out.println("                  [--is-probe-url <url> --is-probe-user <user> --is-probe-password <passwort>]");
        System.out.println("       agwcontrol report [--db-path <datei>] [--output-dir <verzeichnis>]");
        System.out.println();
        System.out.println("  --is-probe-url       IS-Instanz für Remote-Endpoint-Checks, z.B. http://localhost:5555");
        System.out.println("  --is-probe-user      IS-Benutzername (muss in der Gruppe Administrators sein)");
        System.out.println("  --is-probe-password  IS-Passwort");
        System.out.println();
        System.out.println("  Alternativ können IS-PROBE-URL, IS-PROBE-USER und IS-PROBE-PASSWORD");
        System.out.println("  als Custom Fields im jeweiligen KeePass-Eintrag hinterlegt werden.");
    }
}
