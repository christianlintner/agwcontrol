package com.agwcontrol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Erstellt pro Umgebung eine CSV-Datei aus den in der DB gespeicherten
 * APIs, Endpoints und Check-Ergebnissen.
 *
 * <p>Dateiname: {@code report_<environment>_<yyyyMMdd_HHmmss>.csv}
 * <p>Encoding: UTF-8, Trennzeichen: Semikolon, Felder mit Sonderzeichen in "…"
 */
public class DbReportService {

    static final String HEADER =
        "api_name;api_version;api_type;api_active;alias_name;endpoint_url;" +
        "server_host;ping_ok;ping_ms;tcp_ok;tcp_ms;http_status;reachable;error_msg;checked_at";

    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ApiDatabase db;

    public DbReportService(ApiDatabase db) {
        this.db = db;
    }

    /**
     * Erstellt pro Umgebung eine CSV-Datei im angegebenen Verzeichnis.
     * Der Timestamp wird einmalig beim Aufruf gesetzt (gleich für alle Dateien).
     *
     * @return Anzahl der erstellten Dateien
     */
    public int writeReports(Path outputDir) throws SQLException, IOException {
        String ts = LocalDateTime.now().format(TIMESTAMP_FMT);
        List<String> envs = db.loadEnvironments();
        int count = 0;
        for (String env : envs) {
            String csv = buildCsv(env);
            long lines = csv.lines().count() - 1; // ohne Header
            String filename = "report_" + env + "_" + ts + ".csv";
            Path file = outputDir.resolve(filename);
            Files.writeString(file, csv, StandardCharsets.UTF_8);
            System.out.println("Erstellt: " + filename + " (" + lines + " Zeilen)");
            count++;
        }
        return count;
    }

    /**
     * Erstellt den CSV-Inhalt für eine einzelne Umgebung als String.
     * Beginnt immer mit der Header-Zeile.
     */
    public String buildCsv(String environment) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append("\n");

        List<ApiInfo> apis = db.loadApis(environment);
        for (ApiInfo api : apis) {
            List<RoutingEndpoint> endpoints = db.loadEndpoints(environment, api.getId());
            List<CheckRow> checks = loadCheckRows(environment, api.getId());

            // Check-Ergebnisse nach URL gruppieren
            Map<String, List<CheckRow>> checksByUrl = new HashMap<>();
            for (CheckRow cr : checks) {
                checksByUrl.computeIfAbsent(cr.resolvedUrl, k -> new ArrayList<>()).add(cr);
            }

            for (RoutingEndpoint ep : endpoints) {
                String url = ep.getResolvedUrl();
                String alias = ep.isAlias() ? ep.getAliasName() : null;
                List<CheckRow> epChecks = checksByUrl.getOrDefault(url, List.of());

                if (epChecks.isEmpty()) {
                    // Endpoint ohne Check → leere Check-Spalten
                    sb.append(buildRow(api, alias, url, null));
                } else {
                    for (CheckRow cr : epChecks) {
                        sb.append(buildRow(api, alias, url, cr));
                    }
                }
            }
        }
        return sb.toString();
    }

    private String buildRow(ApiInfo api, String alias, String url, CheckRow cr) {
        // Format: 6 API-Felder + 9 Check-Felder = 15 Felder, getrennt durch 14 Semikola
        StringBuilder row = new StringBuilder();
        row.append(csvField(api.getName())).append(";");
        row.append(csvField(api.getVersion())).append(";");
        row.append(csvField(api.getType())).append(";");
        row.append(api.isActive()).append(";");
        row.append(csvField(alias)).append(";");
        row.append(csvField(url));  // kein trailing ";" – Check-Block beginnt mit ";"

        if (cr == null) {
            // 9 leere Check-Felder (Semikola vor jedem Feld)
            row.append(";;;;;;;;;");
        } else {
            row.append(";").append(csvField(cr.serverHost));
            row.append(";").append(cr.pingOk);
            row.append(";").append(cr.pingMs);
            row.append(";").append(cr.tcpOk);
            row.append(";").append(cr.tcpMs);
            row.append(";").append(cr.httpStatus);
            row.append(";").append(cr.reachable);
            row.append(";").append(csvField(cr.errorMsg));
            row.append(";").append(csvField(cr.checkedAt));
        }
        row.append("\n");
        return row.toString();
    }

    /**
     * Lädt alle Check-Zeilen für eine Umgebung und API direkt aus der DB,
     * inklusive server_host und checked_at.
     */
    private List<CheckRow> loadCheckRows(String environment, String apiId) throws SQLException {
        String sql =
            "SELECT server_host, resolved_url, alias_name, ping_ok, ping_ms, " +
            "       tcp_ok, tcp_ms, http_status, reachable, error_msg, checked_at " +
            "FROM endpoint_check_results " +
            "WHERE environment = ? AND api_id = ? " +
            "ORDER BY resolved_url, server_host";
        List<CheckRow> result = new ArrayList<>();
        // Shared connection verwenden (wichtig für In-Memory-DBs in Tests)
        Connection conn = db.openConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, environment);
            ps.setString(2, apiId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CheckRow row = new CheckRow();
                    row.serverHost  = rs.getString("server_host");
                    row.resolvedUrl = rs.getString("resolved_url");
                    row.aliasName   = rs.getString("alias_name");
                    row.pingOk      = rs.getInt("ping_ok") == 1;
                    row.pingMs      = rs.getLong("ping_ms");
                    row.tcpOk       = rs.getInt("tcp_ok") == 1;
                    row.tcpMs       = rs.getLong("tcp_ms");
                    row.httpStatus  = rs.getInt("http_status");
                    row.reachable   = rs.getInt("reachable") == 1;
                    row.errorMsg    = rs.getString("error_msg");
                    row.checkedAt   = rs.getString("checked_at");
                    result.add(row);
                }
            }
        }
        return result;
    }

    /** Umschließt Felder mit Sonderzeichen (; " \n) in Anführungszeichen. */
    static String csvField(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Vollständige DB-Zeile aus endpoint_check_results inkl. server_host + checked_at. */
    static class CheckRow {
        String serverHost;
        String resolvedUrl;
        String aliasName;
        boolean pingOk;
        long pingMs;
        boolean tcpOk;
        long tcpMs;
        int httpStatus;
        boolean reachable;
        String errorMsg;
        String checkedAt;
    }
}
