package com.agwcontrol;

import java.util.List;

public class PingResultFormatter {

    public String format(List<PingResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        boolean hasLabels = results.stream().anyMatch(r -> r.getLabel() != null);

        int hostWidth = results.stream()
                .mapToInt(r -> r.getHost().length())
                .max()
                .orElse(0);

        int labelWidth = hasLabels
                ? results.stream()
                        .mapToInt(r -> r.getLabel() != null ? r.getLabel().length() : 0)
                        .max()
                        .orElse(0)
                : 0;

        StringBuilder sb = new StringBuilder();
        for (PingResult r : results) {
            String status = r.isReachable() ? "OK" : "UNREACHABLE";
            String time   = r.isReachable() ? r.getResponseTimeMs() + "ms" : "-";

            if (hasLabels) {
                String label = r.getLabel() != null ? r.getLabel() : "";
                sb.append(String.format("%-" + hostWidth + "s | %-" + labelWidth + "s | %-11s | %5s%n",
                        r.getHost(), label, status, time));
            } else {
                sb.append(String.format("%-" + hostWidth + "s | %-11s | %5s%n",
                        r.getHost(), status, time));
            }
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
