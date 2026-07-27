package com.agwcontrol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    static final String CROSS_ENV_HEADER_PREFIX =
        "api_name;api_version;api_type;api_active";

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
        String crossEnvCsv = buildCrossEnvCsv();
        String crossEnvFilename = "report_all_environments_" + ts + ".csv";
        Path crossEnvFile = outputDir.resolve(crossEnvFilename);
        Files.writeString(crossEnvFile, crossEnvCsv, StandardCharsets.UTF_8);
        long crossEnvLines = crossEnvCsv.lines().count() - 1;
        System.out.println("Erstellt: " + crossEnvFilename + " (" + crossEnvLines + " Zeilen)");
        count++;
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

    /**
     * Erstellt den Cross-Env-CSV-Inhalt: eine Zeile pro API (api_name + api_version),
     * pro Umgebung ein Spaltenblock mit alias_name, endpoint_url und 8 Check-Feldern.
     * Hat eine API in einer Umgebung mehrere Endpoints, wird nur der erste verwendet.
     */
    public String buildCrossEnvCsv() throws SQLException {
        List<String> envs = db.loadEnvironments();

        // Header aufbauen
        StringBuilder header = new StringBuilder(CROSS_ENV_HEADER_PREFIX);
        for (String env : envs) {
            header.append(";").append(env).append("_alias_name");
            header.append(";").append(env).append("_endpoint_url");
            header.append(";").append(env).append("_ping_ok");
            header.append(";").append(env).append("_ping_ms");
            header.append(";").append(env).append("_tcp_ok");
            header.append(";").append(env).append("_tcp_ms");
            header.append(";").append(env).append("_http_status");
            header.append(";").append(env).append("_reachable");
            header.append(";").append(env).append("_error_msg");
            header.append(";").append(env).append("_checked_at");
        }

        // Pivot-Map: rowKey (api_name|api_version) → (env → PivotEntry)
        // LinkedHashMap behält Einfügereihenfolge; wir sortieren am Ende.
        Map<String, Map<String, PivotEntry>> pivot = new LinkedHashMap<>();
        // Für die Sortierung merken wir uns den ApiInfo je rowKey.
        Map<String, ApiInfo> apiByKey = new LinkedHashMap<>();

        for (String env : envs) {
            List<ApiInfo> apis = db.loadApis(env);
            for (ApiInfo api : apis) {
                String rowKey = api.getName() + "|" + api.getVersion();
                apiByKey.putIfAbsent(rowKey, api);

                List<RoutingEndpoint> endpoints = db.loadEndpoints(env, api.getId());
                if (endpoints.isEmpty()) continue;

                RoutingEndpoint firstEp = endpoints.get(0);
                String url = firstEp.getResolvedUrl();

                // ersten passenden Check-Eintrag für diese URL suchen
                CheckRow matchingCheck = null;
                for (CheckRow cr : loadCheckRows(env, api.getId())) {
                    if (url.equals(cr.resolvedUrl)) {
                        matchingCheck = cr;
                        break;
                    }
                }

                PivotEntry entry = new PivotEntry();
                entry.aliasName  = firstEp.isAlias() ? firstEp.getAliasName() : null;
                entry.endpointUrl = url;
                entry.check      = matchingCheck;

                pivot.computeIfAbsent(rowKey, k -> new LinkedHashMap<>()).put(env, entry);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");

        if (pivot.isEmpty()) {
            sb.append("(keine Daten vorhanden)\n");
            return sb.toString();
        }

        // Zeilen nach api_name + api_version sortiert ausgeben
        List<String> sortedKeys = new ArrayList<>(pivot.keySet());
        sortedKeys.sort(Comparator.naturalOrder());

        for (String rowKey : sortedKeys) {
            ApiInfo api = apiByKey.get(rowKey);
            Map<String, PivotEntry> envMap = pivot.get(rowKey);

            StringBuilder row = new StringBuilder();
            row.append(csvField(api.getName())).append(";");
            row.append(csvField(api.getVersion())).append(";");
            row.append(csvField(api.getType())).append(";");
            row.append(api.isActive());

            for (String env : envs) {
                PivotEntry e = envMap.get(env);
                if (e == null) {
                    // 10 leere Felder
                    row.append(";;;;;;;;;;");
                } else {
                    row.append(";").append(csvField(e.aliasName));
                    row.append(";").append(csvField(e.endpointUrl));
                    if (e.check == null) {
                        // endpoint_url befüllt, 8 Check-Felder leer
                        row.append(";;;;;;;;");
                    } else {
                        row.append(";").append(e.check.pingOk);
                        row.append(";").append(e.check.pingMs);
                        row.append(";").append(e.check.tcpOk);
                        row.append(";").append(e.check.tcpMs);
                        row.append(";").append(e.check.httpStatus);
                        row.append(";").append(e.check.reachable);
                        row.append(";").append(csvField(e.check.errorMsg));
                        row.append(";").append(csvField(e.check.checkedAt));
                    }
                }
            }
            row.append("\n");
            sb.append(row);
        }

        return sb.toString();
    }

    /** Hilfsklasse für einen Env-Spaltenblock im Cross-Env-Report. */
    private static class PivotEntry {
        String aliasName;
        String endpointUrl;
        CheckRow check;
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
