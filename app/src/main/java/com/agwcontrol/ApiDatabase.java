package com.agwcontrol;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Lokale SQLite-Datenbank (Single-File) für APIs und Endpoints pro Umgebung.
 *
 * <p>Die DB-Datei wird beim ersten Aufruf von {@link #initSchema()} angelegt.
 * Für Tests kann {@code ":memory:"} als Pfad übergeben werden.
 *
 * <p>Es wird eine einzelne persistente JDBC-Verbindung gehalten. Das ist für
 * SQLite (Single-Writer) ausreichend und notwendig damit In-Memory-Datenbanken
 * (":memory:") zwischen Aufrufen erhalten bleiben.
 */
public class ApiDatabase {

    private final String dbPath;
    private Connection sharedConnection;

    public ApiDatabase(String dbPath) {
        this.dbPath = dbPath;
    }

    // ---------------------------------------------------------------
    // Schema
    // ---------------------------------------------------------------

    public void initSchema() throws SQLException {
        Connection conn = connect();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS apis (" +
                "  environment  TEXT NOT NULL," +
                "  api_id       TEXT NOT NULL," +
                "  api_name     TEXT," +
                "  api_version  TEXT," +
                "  api_type     TEXT," +
                "  is_active    INTEGER," +
                "  loaded_at    TEXT NOT NULL," +
                "  PRIMARY KEY (environment, api_id)" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS endpoints (" +
                "  environment  TEXT NOT NULL," +
                "  api_id       TEXT NOT NULL," +
                "  alias_name   TEXT," +
                "  resolved_url TEXT," +
                "  is_alias     INTEGER," +
                "  loaded_at    TEXT NOT NULL," +
                "  PRIMARY KEY (environment, api_id, alias_name, resolved_url)" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS endpoint_check_results (" +
                "  environment  TEXT NOT NULL," +
                "  api_id       TEXT NOT NULL," +
                "  server_host  TEXT NOT NULL," +
                "  resolved_url TEXT NOT NULL," +
                "  alias_name   TEXT," +
                "  ping_ok      INTEGER NOT NULL," +
                "  ping_ms      INTEGER NOT NULL," +
                "  tcp_ok       INTEGER NOT NULL," +
                "  tcp_ms       INTEGER NOT NULL," +
                "  http_status  INTEGER NOT NULL," +
                "  reachable    INTEGER NOT NULL," +
                "  error_msg    TEXT," +
                "  checked_at   TEXT NOT NULL," +
                "  PRIMARY KEY (environment, api_id, server_host, resolved_url)" +
                ")"
            );
        }
    }

    // ---------------------------------------------------------------
    // APIs
    // ---------------------------------------------------------------

    /**
     * Speichert die API-Liste für eine Umgebung.
     * Vorhandene Einträge dieser Umgebung werden überschrieben (INSERT OR REPLACE).
     */
    public void saveApis(String environment, List<ApiInfo> apis) throws SQLException {
        String sql = "INSERT OR REPLACE INTO apis " +
                     "(environment, api_id, api_name, api_version, api_type, is_active, loaded_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String now = Instant.now().toString();
        Connection conn = connect();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ApiInfo api : apis) {
                ps.setString(1, environment);
                ps.setString(2, api.getId());
                ps.setString(3, api.getName());
                ps.setString(4, api.getVersion());
                ps.setString(5, api.getType());
                ps.setInt(6, api.isActive() ? 1 : 0);
                ps.setString(7, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Lädt die API-Liste für eine Umgebung aus der DB.
     * Gibt eine leere Liste zurück wenn keine Daten vorhanden.
     */
    public List<ApiInfo> loadApis(String environment) throws SQLException {
        String sql = "SELECT api_id, api_name, api_version, api_type, is_active " +
                     "FROM apis WHERE environment = ? ORDER BY api_name";
        List<ApiInfo> result = new ArrayList<>();
        Connection conn = connect();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, environment);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ApiInfo(
                        rs.getString("api_id"),
                        rs.getString("api_name"),
                        rs.getString("api_version"),
                        rs.getString("api_type"),
                        rs.getInt("is_active") == 1
                    ));
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------

    /**
     * Speichert die Endpoints einer API für eine Umgebung.
     * Löscht zuerst alle vorhandenen Einträge für diese Umgebung+API,
     * dann werden die neuen Einträge eingefügt.
     */
    public void saveEndpoints(String environment, String apiId,
                               List<RoutingEndpoint> endpoints) throws SQLException {
        String deleteSql = "DELETE FROM endpoints WHERE environment = ? AND api_id = ?";
        String insertSql = "INSERT INTO endpoints " +
                           "(environment, api_id, alias_name, resolved_url, is_alias, loaded_at) " +
                           "VALUES (?, ?, ?, ?, ?, ?)";
        String now = Instant.now().toString();
        Connection conn = connect();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setString(1, environment);
                del.setString(2, apiId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                for (RoutingEndpoint ep : endpoints) {
                    ins.setString(1, environment);
                    ins.setString(2, apiId);
                    ins.setString(3, ep.getAliasName());
                    ins.setString(4, ep.getResolvedUrl());
                    ins.setInt(5, ep.isAlias() ? 1 : 0);
                    ins.setString(6, now);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Lädt die Endpoints einer API für eine Umgebung aus der DB.
     * Gibt eine leere Liste zurück wenn keine Daten vorhanden.
     */
    public List<RoutingEndpoint> loadEndpoints(String environment,
                                                String apiId) throws SQLException {
        String sql = "SELECT alias_name, resolved_url, is_alias " +
                     "FROM endpoints WHERE environment = ? AND api_id = ?";
        List<RoutingEndpoint> result = new ArrayList<>();
        Connection conn = connect();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, environment);
            ps.setString(2, apiId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean isAlias = rs.getInt("is_alias") == 1;
                    String aliasName   = rs.getString("alias_name");
                    String resolvedUrl = rs.getString("resolved_url");
                    if (isAlias) {
                        result.add(RoutingEndpoint.alias(aliasName, resolvedUrl));
                    } else {
                        result.add(RoutingEndpoint.direct(resolvedUrl));
                    }
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------
    // Environments
    // ---------------------------------------------------------------

    /**
     * Gibt alle Umgebungen zurück, für die APIs in der DB gespeichert sind.
     * Sortiert alphabetisch.
     */
    public List<String> loadEnvironments() throws SQLException {
        String sql = "SELECT DISTINCT environment FROM apis ORDER BY environment";
        List<String> result = new ArrayList<>();
        Connection conn = connect();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("environment"));
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ---------------------------------------------------------------
    // Check-Ergebnisse
    // ---------------------------------------------------------------

    /**
     * Speichert ein Endpoint-Check-Ergebnis.
     * Vorhandener Eintrag für dieselbe Kombination (environment, api_id,
     * server_host, resolved_url) wird überschrieben (INSERT OR REPLACE).
     */
    public void saveCheckResult(String environment, String apiId,
                                String serverHost, EndpointCheckResult result) throws SQLException {
        String sql =
            "INSERT OR REPLACE INTO endpoint_check_results " +
            "(environment, api_id, server_host, resolved_url, alias_name, " +
            " ping_ok, ping_ms, tcp_ok, tcp_ms, http_status, reachable, error_msg, checked_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = connect();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, environment);
            ps.setString(2, apiId);
            ps.setString(3, serverHost);
            ps.setString(4, result.getUrl());
            ps.setString(5, result.getAliasName());
            ps.setInt(6, result.isPingOk() ? 1 : 0);
            ps.setLong(7, result.getPingMs());
            ps.setInt(8, result.isTcpOk() ? 1 : 0);
            ps.setLong(9, result.getTcpMs());
            ps.setInt(10, result.getHttpStatus());
            ps.setInt(11, result.isReachable() ? 1 : 0);
            ps.setString(12, result.getErrorMsg());
            ps.setString(13, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    /**
     * Lädt alle gespeicherten Check-Ergebnisse für eine Umgebung und API.
     * Gibt eine leere Liste zurück wenn keine Daten vorhanden.
     */
    public List<EndpointCheckResult> loadCheckResults(String environment,
                                                       String apiId) throws SQLException {
        String sql =
            "SELECT resolved_url, alias_name, ping_ok, ping_ms, tcp_ok, tcp_ms, " +
            "       http_status, reachable, error_msg " +
            "FROM endpoint_check_results " +
            "WHERE environment = ? AND api_id = ?";
        List<EndpointCheckResult> result = new ArrayList<>();
        Connection conn = connect();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, environment);
            ps.setString(2, apiId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new EndpointCheckResult(
                        null,
                        null,
                        rs.getString("alias_name"),
                        rs.getString("resolved_url"),
                        rs.getInt("http_status"),
                        rs.getInt("reachable") == 1,
                        rs.getString("error_msg"),
                        rs.getInt("ping_ok") == 1,
                        rs.getLong("ping_ms"),
                        rs.getInt("tcp_ok") == 1,
                        rs.getLong("tcp_ms")
                    ));
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------
    // Hilfsmethoden
    // ---------------------------------------------------------------

    /**
     * Package-private: gibt die persistente Verbindung zurück.
     * Wird von {@link DbReportService} für direkte Abfragen verwendet.
     */
    synchronized Connection openConnection() throws SQLException {
        return connect();
    }

    /** Gibt die persistente Verbindung zurück, öffnet sie bei Bedarf. */
    private synchronized Connection connect() throws SQLException {
        if (sharedConnection == null || sharedConnection.isClosed()) {
            sharedConnection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return sharedConnection;
    }
}
