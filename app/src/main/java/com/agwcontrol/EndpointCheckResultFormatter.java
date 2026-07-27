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

        long okCount = results.stream().filter(this::isOk).count();
        sb.append("  Ergebnis: ").append(okCount).append("/").append(results.size()).append(" OK");
        return sb.toString();
    }

    private void formatSingleApi(StringBuilder sb, List<EndpointCheckResult> results) {
        boolean hasPingTcp = results.stream().anyMatch(r -> r.isPingOk() || r.getPingMs() != -1 || r.isTcpOk() || r.getTcpMs() != -1);
        int aliasWidth     = aliasColumnWidth(results);
        boolean hasAlias   = aliasWidth > 0;
        int urlWidth       = Math.max(3, results.stream()
                .filter(r -> r.getUrl() != null)
                .mapToInt(r -> r.getUrl().length()).max().orElse(0));
        int pingWidth      = hasPingTcp ? Math.max("Ping".length(),
                results.stream().mapToInt(r -> pingCell(r).length()).max().orElse(0)) : 0;
        int tcpWidth       = hasPingTcp ? Math.max("TCP".length(),
                results.stream().mapToInt(r -> tcpCell(r).length()).max().orElse(0)) : 0;
        int httpWidth      = Math.max("HTTP Status".length(),
                results.stream().mapToInt(r -> httpStatusCell(r).length()).max().orElse(0));

        int lineWidth = buildLineWidth(hasAlias, aliasWidth, urlWidth, hasPingTcp, pingWidth, tcpWidth, httpWidth);
        String sep = "─".repeat(lineWidth);

        sb.append(sep).append("\n");
        sb.append(buildHeader(hasAlias, aliasWidth, urlWidth, hasPingTcp, pingWidth, tcpWidth, httpWidth, null));
        sb.append(sep).append("\n");

        for (EndpointCheckResult r : results) {
            sb.append(buildRow(r, hasAlias, aliasWidth, urlWidth, hasPingTcp, pingWidth, tcpWidth, httpWidth, null));
        }
        sb.append(sep).append("\n");
    }

    private void formatMultiApi(StringBuilder sb, List<EndpointCheckResult> results) {
        boolean hasPingTcp = results.stream().anyMatch(r -> r.isPingOk() || r.getPingMs() != -1 || r.isTcpOk() || r.getTcpMs() != -1);
        int apiWidth       = Math.max(3, results.stream()
                .mapToInt(r -> (r.getApiName() + " " + nullSafe(r.getApiVersion())).trim().length())
                .max().orElse(0));
        int aliasWidth     = aliasColumnWidth(results);
        boolean hasAlias   = aliasWidth > 0;
        int urlWidth       = Math.max(3, results.stream()
                .filter(r -> r.getUrl() != null)
                .mapToInt(r -> r.getUrl().length()).max().orElse(0));
        int pingWidth      = hasPingTcp ? Math.max("Ping".length(),
                results.stream().mapToInt(r -> pingCell(r).length()).max().orElse(0)) : 0;
        int tcpWidth       = hasPingTcp ? Math.max("TCP".length(),
                results.stream().mapToInt(r -> tcpCell(r).length()).max().orElse(0)) : 0;
        int httpWidth      = Math.max("HTTP Status".length(),
                results.stream().mapToInt(r -> httpStatusCell(r).length()).max().orElse(0));

        int lineWidth = 2 + apiWidth + 2 + buildLineWidth(hasAlias, aliasWidth, urlWidth, hasPingTcp, pingWidth, tcpWidth, httpWidth);
        String sep = "─".repeat(lineWidth);

        sb.append(sep).append("\n");
        sb.append(buildHeader(hasAlias, aliasWidth, urlWidth, hasPingTcp, pingWidth, tcpWidth, httpWidth, apiWidth));
        sb.append(sep).append("\n");

        for (EndpointCheckResult r : results) {
            sb.append(buildRow(r, hasAlias, aliasWidth, urlWidth, hasPingTcp, pingWidth, tcpWidth, httpWidth, apiWidth));
        }
        sb.append(sep).append("\n");
    }

    private int buildLineWidth(boolean hasAlias, int aliasWidth, int urlWidth,
                               boolean hasPingTcp, int pingWidth, int tcpWidth, int httpWidth) {
        int w = 2 + urlWidth + 2 + httpWidth;
        if (hasAlias)   w += aliasWidth + 2;
        if (hasPingTcp) w += pingWidth + 2 + tcpWidth + 2;
        return w;
    }

    private String buildHeader(boolean hasAlias, int aliasWidth, int urlWidth,
                               boolean hasPingTcp, int pingWidth, int tcpWidth, int httpWidth,
                               Integer apiWidth) {
        StringBuilder h = new StringBuilder("  ");
        if (apiWidth != null) h.append(String.format("%-" + apiWidth + "s  ", "API"));
        if (hasAlias)         h.append(String.format("%-" + aliasWidth + "s  ", "Alias / Endpoint"));
        h.append(String.format("%-" + urlWidth + "s  ", "URL"));
        if (hasPingTcp) {
            h.append(String.format("%-" + pingWidth + "s  ", "Ping"));
            h.append(String.format("%-" + tcpWidth + "s  ", "TCP"));
        }
        h.append("HTTP Status").append("%n");
        return String.format(h.toString());
    }

    private String buildRow(EndpointCheckResult r,
                            boolean hasAlias, int aliasWidth, int urlWidth,
                            boolean hasPingTcp, int pingWidth, int tcpWidth, int httpWidth,
                            Integer apiWidth) {
        StringBuilder row = new StringBuilder("  ");
        if (apiWidth != null) {
            String apiLabel = (r.getApiName() + " " + nullSafe(r.getApiVersion())).trim();
            row.append(String.format("%-" + apiWidth + "s  ", apiLabel));
        }
        if (hasAlias) {
            String label = r.getAliasName() != null
                    ? r.getAliasName() + " (Alias)"
                    : nullSafe(r.getUrl());
            row.append(String.format("%-" + aliasWidth + "s  ", label));
        }
        String displayUrl = (hasAlias && r.getAliasName() != null) ? nullSafe(r.getUrl()) : "(direkt)";
        if (!hasAlias) displayUrl = nullSafe(r.getUrl());
        row.append(String.format("%-" + urlWidth + "s  ", displayUrl));
        if (hasPingTcp) {
            row.append(String.format("%-" + pingWidth + "s  ", pingCell(r)));
            row.append(String.format("%-" + tcpWidth + "s  ", tcpCell(r)));
        }
        row.append(httpStatusCell(r)).append("%n");
        return String.format(row.toString());
    }

    /** Ping-Spalteninhalt: "OK 12ms" / "FAIL" */
    private String pingCell(EndpointCheckResult r) {
        if (r.getPingMs() == -1 && !r.isPingOk()) return "FAIL";
        return "OK " + r.getPingMs() + "ms";
    }

    /** TCP-Spalteninhalt: "OPEN 8ms" / "CLOSED" */
    private String tcpCell(EndpointCheckResult r) {
        if (r.getTcpMs() == -1 && !r.isTcpOk()) return "CLOSED";
        return "OPEN " + r.getTcpMs() + "ms";
    }

    /** HTTP Status-Spalteninhalt: "OK (200)" / "FAIL (msg)" / "FAIL" */
    private String httpStatusCell(EndpointCheckResult r) {
        if (r.isReachable()) {
            return "OK (" + r.getHttpStatus() + ")";
        }
        if (r.getErrorMsg() != null && !r.getErrorMsg().isEmpty()) {
            return "FAIL (" + r.getErrorMsg() + ")";
        }
        return "FAIL";
    }

    /** Ein Endpoint gilt als OK wenn Ping + TCP + HTTP alle erfolgreich. */
    private boolean isOk(EndpointCheckResult r) {
        return r.isPingOk() && r.isTcpOk() && r.isReachable();
    }

    /** Berechnet die Breite der Alias-Spalte; 0 wenn keine Ergebnisse einen Alias haben. */
    private int aliasColumnWidth(List<EndpointCheckResult> results) {
        boolean anyAlias = results.stream().anyMatch(r -> r.getAliasName() != null);
        if (!anyAlias) return 0;
        return Math.max("Alias / Endpoint".length(),
                results.stream()
                        .filter(r -> r.getAliasName() != null)
                        .mapToInt(r -> r.getAliasName().length() + " (Alias)".length())
                        .max().orElse(0));
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
