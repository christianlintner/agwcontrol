package com.agwcontrol;

import java.util.List;

public class TcpCheckResultFormatter {

    public String format(List<TcpCheckResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        boolean hasLabels = results.stream().anyMatch(r -> r.getLabel() != null);

        int colWidth = results.stream()
                .mapToInt(r -> (r.getHost() + ":" + r.getPort()).length())
                .max()
                .orElse(0);

        int labelWidth = hasLabels
                ? results.stream()
                        .mapToInt(r -> r.getLabel() != null ? r.getLabel().length() : 0)
                        .max()
                        .orElse(0)
                : 0;

        StringBuilder sb = new StringBuilder();
        for (TcpCheckResult r : results) {
            String hostPort = r.getHost() + ":" + r.getPort();
            String status   = r.isOpen() ? "OPEN" : "CLOSED";
            String time     = r.isOpen() ? r.getResponseTimeMs() + "ms" : "-";

            if (hasLabels) {
                String label = r.getLabel() != null ? r.getLabel() : "";
                sb.append(String.format("%-" + colWidth + "s | %-" + labelWidth + "s | %-6s | %5s%n",
                        hostPort, label, status, time));
            } else {
                sb.append(String.format("%-" + colWidth + "s | %-6s | %5s%n",
                        hostPort, status, time));
            }
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
