package com.agwcontrol;

import java.util.List;

public class EndpointCheckResultFormatter {

    /**
     * Formatiert eine Liste von Endpoint-Check-Ergebnissen als Tabelle.
     * Bei Ergebnissen einer einzelnen API wird der API-Name in der Überschrift
     * ausgegeben; bei mehreren APIs erscheint eine zusätzliche API-Spalte.
     *
     * @param serverLabel Hostname des Servers (für die Überschrift)
     * @param results     Liste aller Endpoint-Ergebnisse
     */
    public String format(String serverLabel, List<EndpointCheckResult> results) {
        if (results.isEmpty()) {
            return "Keine Endpoints gefunden.";
        }

        long distinctApis = results.stream()
                .map(EndpointCheckResult::getApiName)
                .distinct()
                .count();
        boolean multiApi = distinctApis > 1;

        StringBuilder sb = new StringBuilder();

        if (multiApi) {
            sb.append("Endpoint-Check für alle APIs auf ").append(serverLabel).append("\n");
            formatMultiApi(sb, results);
        } else {
            EndpointCheckResult first = results.get(0);
            String apiLabel = first.getApiName() + " " + nullSafe(first.getApiVersion());
            sb.append("Endpoint-Check für ").append(apiLabel.trim())
              .append(" auf ").append(serverLabel).append("\n");
            formatSingleApi(sb, results);
        }

        long reachableCount = results.stream().filter(EndpointCheckResult::isReachable).count();
        sb.append("  ").append(results.size())
          .append(results.size() == 1 ? " Endpoint geprüft, " : " Endpoints geprüft, ")
          .append(reachableCount).append(" erreichbar");
        return sb.toString();
    }

    private void formatSingleApi(StringBuilder sb, List<EndpointCheckResult> results) {
        int urlWidth    = Math.max(3, results.stream().mapToInt(r -> r.getUrl().length()).max().orElse(0));
        int statusWidth = 6; // "Status"

        String rowFmt   = "  %-" + urlWidth + "s  %-" + statusWidth + "s  %s%n";
        int lineWidth   = 2 + urlWidth + 2 + statusWidth + 2 + 10;
        String sep      = "─".repeat(lineWidth);

        sb.append(sep).append("\n");
        sb.append(String.format(rowFmt, "URL", "Status", "Erreichbar"));
        sb.append(sep).append("\n");
        for (EndpointCheckResult r : results) {
            String status = r.getHttpStatus() == 0 ? "-" : String.valueOf(r.getHttpStatus());
            String reach  = r.isReachable() ? "JA" : reachableNo(r);
            sb.append(String.format(rowFmt, r.getUrl(), status, reach));
        }
        sb.append(sep).append("\n");
    }

    private void formatMultiApi(StringBuilder sb, List<EndpointCheckResult> results) {
        int apiWidth    = Math.max(3, results.stream()
                .mapToInt(r -> (r.getApiName() + " " + nullSafe(r.getApiVersion())).trim().length())
                .max().orElse(0));
        int urlWidth    = Math.max(3, results.stream().mapToInt(r -> r.getUrl().length()).max().orElse(0));
        int statusWidth = 6; // "Status"

        String rowFmt = "  %-" + apiWidth + "s  %-" + urlWidth + "s  %-" + statusWidth + "s  %s%n";
        int lineWidth = 2 + apiWidth + 2 + urlWidth + 2 + statusWidth + 2 + 10;
        String sep    = "─".repeat(lineWidth);

        sb.append(sep).append("\n");
        sb.append(String.format(rowFmt, "API", "URL", "Status", "Erreichbar"));
        sb.append(sep).append("\n");
        for (EndpointCheckResult r : results) {
            String apiLabel = (r.getApiName() + " " + nullSafe(r.getApiVersion())).trim();
            String status   = r.getHttpStatus() == 0 ? "-" : String.valueOf(r.getHttpStatus());
            String reach    = r.isReachable() ? "JA" : reachableNo(r);
            sb.append(String.format(rowFmt, apiLabel, r.getUrl(), status, reach));
        }
        sb.append(sep).append("\n");
    }

    private String reachableNo(EndpointCheckResult r) {
        if (r.getErrorMsg() != null && !r.getErrorMsg().isEmpty()) {
            return "NEIN  (" + r.getErrorMsg() + ")";
        }
        return "NEIN";
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
