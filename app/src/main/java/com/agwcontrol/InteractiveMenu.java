package com.agwcontrol;

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
            out.println("Ungültige Eingabe. Bitte [1], [2], [b] oder [q] eingeben.");
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

    private int parseIndex(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
