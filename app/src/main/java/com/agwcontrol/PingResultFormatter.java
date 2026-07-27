package com.agwcontrol;

import java.util.List;

public class PingResultFormatter {

    public String format(List<PingResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        // Breiteste Host-Spalte ermitteln
        int hostWidth = results.stream()
                .mapToInt(r -> r.getHost().length())
                .max()
                .orElse(0);

        StringBuilder sb = new StringBuilder();
        for (PingResult r : results) {
            String status = r.isReachable() ? "OK" : "UNREACHABLE";
            String time   = r.isReachable() ? r.getResponseTimeMs() + "ms" : "-";

            sb.append(String.format("%-" + hostWidth + "s | %-11s | %5s%n",
                    r.getHost(), status, time));
        }
        // Letztes Zeilenende entfernen
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
