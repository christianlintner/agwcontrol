package com.agwcontrol;

import java.util.List;

public class TcpCheckResultFormatter {

    public String format(List<TcpCheckResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        // Breiteste host:port-Spalte ermitteln
        int colWidth = results.stream()
                .mapToInt(r -> (r.getHost() + ":" + r.getPort()).length())
                .max()
                .orElse(0);

        StringBuilder sb = new StringBuilder();
        for (TcpCheckResult r : results) {
            String hostPort = r.getHost() + ":" + r.getPort();
            String status   = r.isOpen() ? "OPEN" : "CLOSED";
            String time     = r.isOpen() ? r.getResponseTimeMs() + "ms" : "-";

            sb.append(String.format("%-" + colWidth + "s | %-6s | %5s%n",
                    hostPort, status, time));
        }
        // Letztes Zeilenende entfernen
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
