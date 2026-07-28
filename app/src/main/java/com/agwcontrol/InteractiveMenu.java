package com.agwcontrol;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class InteractiveMenu {

    private final List<ServerGroup> groups;
    private final Scanner scanner;
    private final PrintStream out;

    private final PingService pingService = new PingService();
    private final PingResultFormatter pingFormatter = new PingResultFormatter();
    private final TcpCheckService tcpService = new TcpCheckService();
    private final TcpCheckResultFormatter tcpFormatter = new TcpCheckResultFormatter();
    private final AgwApiService agwApiService = new AgwApiService();
    private final ApiInfoFormatter apiInfoFormatter = new ApiInfoFormatter();
    private final EndpointCheckService endpointCheckService = new EndpointCheckService();
    private final EndpointCheckResultFormatter endpointCheckFormatter = new EndpointCheckResultFormatter();

    private final ApiDatabase apiDatabase;
    private final DbCacheConfig cacheConfig = new DbCacheConfig();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public InteractiveMenu(List<ServerGroup> groups, InputStream in, PrintStream out) {
        this(groups, in, out, "agwcontrol.db");
    }

    public InteractiveMenu(List<ServerGroup> groups, InputStream in, PrintStream out, String dbPath) {
        this.groups = groups;
        this.scanner = new Scanner(in);
        this.out = out;
        this.apiDatabase = new ApiDatabase(dbPath);
        try {
            this.apiDatabase.initSchema();
        } catch (SQLException e) {
            this.out.println("Warnung: Datenbank konnte nicht initialisiert werden: " + e.getMessage());
        }
    }

    private String ts() {
        return "[" + LocalTime.now().format(TIME_FMT) + "] ";
    }

    public void run() {
        while (true) {
            printMainMenu();
            String input = scanner.nextLine().trim();

            if ("q".equalsIgnoreCase(input)) {
                break;
            }

            if ("a".equalsIgnoreCase(input)) {
                runActionMenu(groups);
                continue;
            }

            if ("r".equalsIgnoreCase(input)) {
                runReport();
                continue;
            }

            int idx = parseIndex(input);
            if (idx < 1 || idx > groups.size()) {
                out.println("Ungültige Eingabe. Bitte eine Zahl zwischen 1 und " + groups.size() + ", [a], [r] oder [q] eingeben.");
                continue;
            }

            List<ServerGroup> selected = List.of(groups.get(idx - 1));
            runActionMenu(selected);
        }
    }

    private void printMainMenu() {
        out.println();
        out.println("AGW-Control");
        out.println("─────────────────────────────────────");
        out.println("Verfügbare Umgebungen:");
        for (int i = 0; i < groups.size(); i++) {
            ServerGroup g = groups.get(i);
            out.printf("  [%d]  %-15s (%d %s)%n",
                    i + 1,
                    g.getName(),
                    g.getServers().size(),
                    g.getServers().size() == 1 ? "Server" : "Server");
        }
        out.println("  [a]  Alle Umgebungen");
        out.println("  ─────────────────────────────────────");
        out.println("  [r]  Report erstellen (CSV)");
        out.println("  [q]  Beenden");
        out.print("Auswahl: ");
    }

    private void runActionMenu(List<ServerGroup> selected) {
        String label = selected.size() == 1
                ? selected.get(0).getName()
                : "Alle Umgebungen";
        int totalServers = selected.stream().mapToInt(g -> g.getServers().size()).sum();

        while (true) {
            // Ermittle Umgebungsname für den DB-leer-Hinweis
            String envName = selected.size() == 1 ? selected.get(0).getName() : "alle";
            String dbHint = "";
            if (!cacheConfig.isUseDbForApis()) {
                // Prüfe ob DB bereits Daten für diese Umgebung hat
                boolean hasData = false;
                try {
                    hasData = selected.size() == 1
                            && !apiDatabase.loadApis(selected.get(0).getName()).isEmpty();
                } catch (SQLException ignored) {}
                if (!hasData) {
                    dbHint = "  ⚠ noch keine Daten für " + envName;
                }
            }

            out.println();
            out.println("Aktion für " + label + " (" + totalServers + " Server):"
                    + "  Cache-Modus: [" + cacheConfig.label() + "]");
            out.println("  [1]  Ping");
            out.println("  [2]  TCP-Check");
            out.println("  [3]  APIs auflisten");
            out.println("  [4]  Endpoints auflisten");
            out.println("  [5]  Endpoint-Check");
            out.println("  ─────────────────────────────────────");
            out.println("  [c]  Cache umschalten  →  würde wechseln zu: "
                    + cacheConfig.labelAfterToggle() + dbHint);
            out.println("  [b]  Zurück");
            out.println("  [q]  Beenden");
            out.print("Auswahl: ");

            String input = scanner.nextLine().trim();

            if ("q".equalsIgnoreCase(input)) {
                System.exit(0);
            }
            if ("b".equalsIgnoreCase(input)) {
                return;
            }
            if ("c".equalsIgnoreCase(input)) {
                cacheConfig.toggleAll();
                continue;
            }
            if ("1".equals(input)) {
                runPing(selected);
                return;
            }
            if ("2".equals(input)) {
                runTcpCheck(selected);
                return;
            }
            if ("3".equals(input)) {
                ServerConfig server = selectServer(selected);
                if (server != null) {
                    runApiList(server, envName);
                }
                return;
            }
            if ("4".equals(input)) {
                ServerConfig server = selectServer(selected);
                if (server != null) {
                    ApiSelection sel = selectApis(server, envName);
                    if (sel != null) {
                        runEndpointList(server, sel, envName);
                    }
                }
                return;
            }
            if ("5".equals(input)) {
                ServerConfig server = selectServer(selected);
                if (server != null) {
                    ApiSelection sel = selectApis(server, envName);
                    if (sel != null) {
                        runEndpointCheck(server, sel, envName);
                    }
                }
                return;
            }
            out.println("Ungültige Eingabe. Bitte [1], [2], [3], [4], [5], [c], [b] oder [q] eingeben.");
        }
    }

    private void runPing(List<ServerGroup> selected) {
        List<PingResult> results = new ArrayList<>();
        for (ServerGroup group : selected) {
            for (ServerConfig server : group.getServers()) {
                results.add(pingService.ping(server));
            }
        }
        out.println();
        out.println(pingFormatter.format(results));
    }

    private void runTcpCheck(List<ServerGroup> selected) {
        List<TcpCheckResult> results = new ArrayList<>();
        for (ServerGroup group : selected) {
            for (ServerConfig server : group.getServers()) {
                results.add(tcpService.check(server));
            }
        }
        out.println();
        out.println(tcpFormatter.format(results));
    }

    /**
     * Bei genau einem Server in der Auswahl wird dieser direkt zurückgegeben.
     * Bei mehreren Servern wird ein Auswahlmenü angezeigt.
     * Gibt null zurück wenn der Nutzer abbricht.
     */
    private ServerConfig selectServer(List<ServerGroup> selected) {
        List<ServerConfig> all = new ArrayList<>();
        for (ServerGroup g : selected) {
            all.addAll(g.getServers());
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        while (true) {
            out.println();
            out.println("Server auswählen:");
            for (int i = 0; i < all.size(); i++) {
                out.printf("  [%d]  %s%n", i + 1, all.get(i).getHost());
            }
            out.println("  [b]  Zurück");
            out.print("Auswahl: ");

            String input = scanner.nextLine().trim();
            if ("b".equalsIgnoreCase(input)) {
                return null;
            }
            int idx = parseIndex(input);
            if (idx >= 1 && idx <= all.size()) {
                return all.get(idx - 1);
            }
            out.println("Ungültige Eingabe.");
        }
    }

    /**
     * Zeigt die API-Liste (inkl. Routing-Endpoint-Name) und lässt den Nutzer eine oder alle auswählen.
     * Gibt eine Liste mit einer API (Einzelwahl), die volle Liste ([a]) oder
     * null bei Abbruch zurück.
     */
    /** Ergebnis der API-Auswahl: gewählte APIs. */
    private static final class ApiSelection {
        final List<ApiInfo> apis;

        ApiSelection(List<ApiInfo> apis) {
            this.apis = apis;
        }
    }

    /**
     * Schritt 1: Lädt API-Liste + native Endpoints (einmalig), zeigt Liste mit
     * Routing-Endpoint-Label an, lässt den Nutzer eine oder alle auswählen.
     * Gibt null zurück bei Abbruch.
     */
    private ApiSelection selectApis(ServerConfig server, String environment) {
        out.println();
        String[] hint = new String[1];
        out.print("Lade API-Liste von " + server.getHost() + " ...");
        List<ApiInfo> apis;
        try {
            apis = agwApiService.listApis(server, environment, apiDatabase, cacheConfig, hint);
            out.println(" [" + hint[0] + "]");
        } catch (IOException e) {
            out.println();
            out.println("Fehler beim Abrufen der APIs: " + e.getMessage());
            return null;
        }
        if (apis.isEmpty()) {
            out.println("Keine APIs gefunden.");
            return null;
        }

        while (true) {
            out.println();
            out.println("API auswählen für " + server.getHost() + ":");
            for (int i = 0; i < apis.size(); i++) {
                ApiInfo a = apis.get(i);
                out.printf("  [%d]  %-30s %-10s %s%n",
                        i + 1, a.getName(), nullSafe(a.getVersion()), nullSafe(a.getType()));
            }
            out.println("  [a]  Alle APIs");
            out.println("  [b]  Zurück");
            out.print("Auswahl: ");

            String input = scanner.nextLine().trim();
            if ("b".equalsIgnoreCase(input)) {
                return null;
            }
            if ("a".equalsIgnoreCase(input)) {
                return new ApiSelection(apis);
            }
            int idx = parseIndex(input);
            if (idx >= 1 && idx <= apis.size()) {
                return new ApiSelection(List.of(apis.get(idx - 1)));
            }
            out.println("Ungültige Eingabe.");
        }
    }

    private void runEndpointList(ServerConfig server, ApiSelection sel, String environment) {
        out.println();
        boolean foundAny = false;
        for (ApiInfo api : sel.apis) {
            String[] hint = new String[1];
            out.print(ts() + "Lade nativen Endpoint für " + api.getName() + " ...");
            List<RoutingEndpoint> endpoints;
            try {
                endpoints = agwApiService.getNativeEndpoints(
                        server, api.getId(), environment, apiDatabase, cacheConfig, hint);
                out.println(" [" + hint[0] + "]");
            } catch (IOException e) {
                out.println();
                out.println(ts() + "  Fehler: " + e.getMessage());
                continue;
            }
            if (endpoints.isEmpty()) {
                out.println(ts() + "  Kein nativer Endpoint gefunden.");
                continue;
            }
            for (RoutingEndpoint ep : endpoints) {
                String url = ep.getResolvedUrl();
                if (url == null || url.isEmpty()) {
                    out.println(ts() + "  Alias '" + ep.getAliasName() + "' konnte nicht aufgelöst werden.");
                    continue;
                }
                foundAny = true;
                out.println(ts() + "  " + api.getName() + " " + nullSafe(api.getVersion()).trim()
                        + ": " + (ep.isAlias() ? ep.getAliasName() + " → " : "") + url);
            }
        }
        if (!foundAny) {
            out.println();
            out.println("Keine Endpoints gefunden.");
        }
    }

    /**
     * Schritt 2: Lädt native Endpoints für die gewählten APIs (1 Request pro API),
     * führt den HTTP-Check durch und gibt das Ergebnis aus.
     */
    private void runEndpointCheck(ServerConfig server, ApiSelection sel, String environment) {
        List<EndpointCheckResult> results = new ArrayList<>();
        for (ApiInfo api : sel.apis) {
            String[] hint = new String[1];
            out.print(ts() + "Lade nativen Endpoint für " + api.getName() + " ...");
            List<RoutingEndpoint> endpoints;
            try {
                endpoints = agwApiService.getNativeEndpoints(
                        server, api.getId(), environment, apiDatabase, cacheConfig, hint);
                out.println(" [" + hint[0] + "]");
            } catch (IOException e) {
                out.println();
                out.println(ts() + "  Fehler: " + e.getMessage());
                continue;
            }
            if (endpoints.isEmpty()) {
                out.println(ts() + "  Kein nativer Endpoint gefunden.");
                continue;
            }
            for (RoutingEndpoint ep : endpoints) {
                String url = ep.getResolvedUrl();
                if (url == null || url.isEmpty()) {
                    out.println(ts() + "  Alias '" + ep.getAliasName() + "' konnte nicht aufgelöst werden.");
                    continue;
                }
                out.println(ts() + "  Prüfe Ping/TCP/HTTP für " + (ep.isAlias() ? ep.getAliasName() + " → " : "") + url + " ...");
                try {
                    EndpointCheckResult r = endpointCheckService.check(
                            api.getName(), api.getVersion(),
                            ep.isAlias() ? ep.getAliasName() : null,
                            url,
                            environment, api.getId(), server.getHost(),
                            apiDatabase);
                    results.add(r);
                } catch (java.sql.SQLException e) {
                    out.println(ts() + "  Warnung: Check-Ergebnis konnte nicht gespeichert werden: " + e.getMessage());
                    // Ergebnis trotzdem anzeigen (ohne DB-Speicherung)
                    EndpointCheckResult r = endpointCheckService.check(api.getName(), api.getVersion(), url);
                    if (ep.isAlias()) {
                        r = new EndpointCheckResult(r.getApiName(), r.getApiVersion(),
                                ep.getAliasName(), r.getUrl(), r.getHttpStatus(), r.isReachable(), r.getErrorMsg(),
                                r.isPingOk(), r.getPingMs(), r.isTcpOk(), r.getTcpMs());
                    }
                    results.add(r);
                }
            }
        }
        out.println();
        if (results.isEmpty()) {
            out.println("Keine Endpoints gefunden.");
            return;
        }
        out.println(endpointCheckFormatter.format(server.getHost(), results));
    }

    private void runReport() {
        out.println();
        out.print("Ausgabeverzeichnis [.]: ");
        String dirInput = scanner.nextLine().trim();
        java.nio.file.Path outputDir = java.nio.file.Path.of(dirInput.isEmpty() ? "." : dirInput);
        try {
            boolean hasData = !apiDatabase.loadEnvironments().isEmpty();
            if (!hasData) {
                out.println("Keine Daten in der Datenbank. Bitte zuerst APIs und Endpoints laden.");
                return;
            }
            new DbReportService(apiDatabase).writeReports(outputDir);
        } catch (java.sql.SQLException | java.io.IOException e) {
            out.println("Fehler beim Report: " + e.getMessage());
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private void runApiList(ServerConfig server, String environment) {
        out.println();
        String[] hint = new String[1];
        out.print("Lade APIs von " + server.getHost() + " ...");
        try {
            List<ApiInfo> apis = agwApiService.listApis(server, environment, apiDatabase, cacheConfig, hint);
            out.println(" [" + hint[0] + "]");
            out.println(apiInfoFormatter.format(server.getHost(), apis));
        } catch (IOException e) {
            out.println();
            out.println("Fehler beim Abrufen der APIs: " + e.getMessage());
        }
    }

    private int parseIndex(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
