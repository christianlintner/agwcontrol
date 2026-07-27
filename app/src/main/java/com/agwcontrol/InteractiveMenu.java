package com.agwcontrol;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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

    public InteractiveMenu(List<ServerGroup> groups, InputStream in, PrintStream out) {
        this.groups = groups;
        this.scanner = new Scanner(in);
        this.out = out;
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

            int idx = parseIndex(input);
            if (idx < 1 || idx > groups.size()) {
                out.println("Ungültige Eingabe. Bitte eine Zahl zwischen 1 und " + groups.size() + ", [a] oder [q] eingeben.");
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
        out.println("  [q]  Beenden");
        out.print("Auswahl: ");
    }

    private void runActionMenu(List<ServerGroup> selected) {
        String label = selected.size() == 1
                ? selected.get(0).getName()
                : "Alle Umgebungen";
        int totalServers = selected.stream().mapToInt(g -> g.getServers().size()).sum();

        while (true) {
            out.println();
            out.println("Aktion für " + label + " (" + totalServers + " Server):");
            out.println("  [1]  Ping");
            out.println("  [2]  TCP-Check");
            out.println("  [3]  APIs auflisten");
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
                    runApiList(server);
                }
                return;
            }
            out.println("Ungültige Eingabe. Bitte [1], [2], [3], [b] oder [q] eingeben.");
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

    private void runApiList(ServerConfig server) {
        out.println();
        out.println("Lade APIs von " + server.getHost() + " ...");
        try {
            List<ApiInfo> apis = agwApiService.listApis(server);
            out.println(apiInfoFormatter.format(server.getHost(), apis));
        } catch (IOException e) {
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
