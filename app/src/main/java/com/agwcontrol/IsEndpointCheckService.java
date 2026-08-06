package com.agwcontrol;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Delegates all endpoint connectivity checks to a remote webMethods Integration
 * Server instance via the checkRAD REST API Descriptor.
 *
 * <p>The IS server exposes three separate probe endpoints:
 * <pre>
 *   GET {IS-base}/ping?host={host}
 *   GET {IS-base}/tcp?host={host}&amp;port={port}
 *   GET {IS-base}/http?url={encodedUrl}
 * </pre>
 * This service calls them <em>sequentially</em> with early-exit:
 * if Ping fails, TCP and HTTP are skipped; if TCP is closed, HTTP is skipped.
 *
 * <p>Authentication uses HTTP Basic Auth with the credentials supplied in
 * {@link IsEndpointCheckConfig}. The IS instance must have the
 * {@code OEBB_Infra_Pro_AGWCheck} package deployed and the calling user must
 * be a member of the {@code Administrators} group.</p>
 */
public class IsEndpointCheckService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    private static final Pattern JSON_STRING =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    private final IsEndpointCheckConfig config;
    private final HttpDebugConfig httpDebugConfig;
    private final PrintStream debugStream;

    public IsEndpointCheckService(IsEndpointCheckConfig config) {
        this(config, new HttpDebugConfig(), System.out);
    }

    public IsEndpointCheckService(IsEndpointCheckConfig config,
                                  HttpDebugConfig httpDebugConfig,
                                  PrintStream debugOut) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        this.config          = config;
        this.httpDebugConfig = httpDebugConfig != null ? httpDebugConfig : new HttpDebugConfig();
        this.debugStream     = debugOut != null ? debugOut : System.out;
    }

    // -----------------------------------------------------------------------
    // Public API — mirrors EndpointCheckService
    // -----------------------------------------------------------------------

    /**
     * Runs the endpoint check remotely using three sequential IS probe endpoints
     * (ping → tcp → http) with early-exit on failure.
     *
     * @param apiName    Name of the API being checked (for result labelling only)
     * @param apiVersion Version of the API (for result labelling only)
     * @param urlStr     Backend endpoint URL to probe
     * @return populated {@link EndpointCheckResult}; never {@code null}
     */
    public EndpointCheckResult check(String apiName, String apiVersion, String urlStr) {
        debugProbeConfig();

        // Extract host + port from URL
        String host;
        int port;
        try {
            URL parsed = new URI(urlStr).toURL();
            host = parsed.getHost();
            port = parsed.getPort();
            if (port == -1) {
                port = "https".equalsIgnoreCase(parsed.getProtocol()) ? 443 : 80;
            }
        } catch (Exception e) {
            return errorResult(apiName, apiVersion, null, urlStr,
                    "Ungültige URL: " + e.getMessage());
        }

        // 1. Ping
        PingProbeResult ping;
        try {
            String json = callPingEndpoint(host);
            ping = parsePingResponse(json);
        } catch (IOException e) {
            return errorResult(apiName, apiVersion, null, urlStr,
                    "IS probe unreachable: " + e.getMessage());
        }
        debugMsg("[HTTP-DEBUG] IS PING " + host + " → "
                + (ping.reachable ? "OK " + ping.responseTimeMs + "ms" : "FAIL"));
        if (!ping.reachable) {
            return new EndpointCheckResult(apiName, apiVersion, null, urlStr,
                    0, false, "Ping fehlgeschlagen",
                    false, ping.responseTimeMs, false, -1L);
        }

        // 2. TCP
        TcpProbeResult tcp;
        try {
            String json = callTcpEndpoint(host, port);
            tcp = parseTcpResponse(json);
        } catch (IOException e) {
            return errorResult(apiName, apiVersion, null, urlStr,
                    "IS probe unreachable: " + e.getMessage());
        }
        debugMsg("[HTTP-DEBUG] IS TCP  " + host + ":" + port + " → "
                + (tcp.open ? "OPEN " + tcp.responseTimeMs + "ms" : "CLOSED"));
        if (!tcp.open) {
            return new EndpointCheckResult(apiName, apiVersion, null, urlStr,
                    0, false, "TCP nicht erreichbar",
                    true, ping.responseTimeMs, false, tcp.responseTimeMs);
        }

        // 3. HTTP
        HttpProbeResult http;
        try {
            String json = callHttpEndpoint(urlStr);
            http = parseHttpResponse(json, urlStr);
        } catch (IOException e) {
            return errorResult(apiName, apiVersion, null, urlStr,
                    "IS probe unreachable: " + e.getMessage());
        }
        debugMsg("[HTTP-DEBUG] IS HTTP " + urlStr + " → "
                + (http.status > 0 ? "Status " + http.status
                        : "FAIL" + (http.errorMsg.isEmpty() ? "" : " (" + http.errorMsg + ")")));

        return new EndpointCheckResult(apiName, apiVersion, null, http.url,
                http.status, http.reachable, http.errorMsg,
                true, ping.responseTimeMs, true, tcp.responseTimeMs);
    }

    /**
     * Runs the full endpoint check remotely, saves the result to the database,
     * and returns the result.
     *
     * @param aliasName   Endpoint alias name (may be {@code null})
     * @param environment Environment label (e.g. "PROD")
     * @param apiId       API ID in the database
     * @param serverHost  Hostname of the AGW server being checked
     * @param db          Database to persist the result in
     */
    public EndpointCheckResult check(String apiName, String apiVersion,
                                     String aliasName, String urlStr,
                                     String environment, String apiId, String serverHost,
                                     ApiDatabase db) throws SQLException {
        debugProbeConfig();
        EndpointCheckResult result = check(apiName, apiVersion, urlStr);
        // Re-wrap with alias name if provided
        EndpointCheckResult withAlias = aliasName != null
                ? new EndpointCheckResult(result.getApiName(), result.getApiVersion(),
                        aliasName, result.getUrl(),
                        result.getHttpStatus(), result.isReachable(), result.getErrorMsg(),
                        result.isPingOk(), result.getPingMs(),
                        result.isTcpOk(), result.getTcpMs())
                : result;
        db.saveCheckResult(environment, apiId, serverHost, withAlias);
        return withAlias;
    }

    // -----------------------------------------------------------------------
    // IS HTTP calls — one method per probe
    // -----------------------------------------------------------------------

    /**
     * Calls {@code GET /ping?host={host}} on the IS RAD and returns the raw JSON body.
     * Logs the full request URL, HTTP status, and response body when debug is enabled.
     */
    String callPingEndpoint(String host) throws IOException {
        String fullUrl = config.buildBaseUrl() + "/ping?host=" + encodeQueryParam(host);
        return callIsEndpoint(fullUrl);
    }

    /**
     * Calls {@code GET /tcp?host={host}&port={port}} on the IS RAD and returns the raw JSON body.
     * Logs the full request URL, HTTP status, and response body when debug is enabled.
     */
    String callTcpEndpoint(String host, int port) throws IOException {
        String fullUrl = config.buildBaseUrl() + "/tcp?host=" + encodeQueryParam(host)
                + "&port=" + port;
        return callIsEndpoint(fullUrl);
    }

    /**
     * Calls {@code GET /http?url={encodedUrl}} on the IS RAD and returns the raw JSON body.
     * Logs the full request URL, HTTP status, and response body when debug is enabled.
     */
    String callHttpEndpoint(String endpointUrl) throws IOException {
        String fullUrl = config.buildBaseUrl() + "/http?url=" + encodeQueryParam(endpointUrl);
        return callIsEndpoint(fullUrl);
    }

    /**
     * Shared HTTP call: opens a connection to {@code fullUrl}, logs request/status/body,
     * validates the IS response code, and returns the response body.
     */
    private String callIsEndpoint(String fullUrl) throws IOException {
        URL url = new URL(fullUrl);

        debugMsg("[HTTP-DEBUG] IS GET " + fullUrl);

        HttpURLConnection conn = openConnection(url);
        try {
            int status = conn.getResponseCode();
            debugMsg("[HTTP-DEBUG] IS Status " + status);
            if (status == 401) {
                throw new IOException(
                        "IS authentication failed (HTTP 401) — check IS credentials in config");
            }
            if (status == 403) {
                throw new IOException(
                        "IS access denied (HTTP 403) — user must be in Administrators group");
            }
            if (status < 200 || status >= 300) {
                throw new IOException("IS returned HTTP " + status + " for " + fullUrl);
            }
            String body = readBody(conn);
            debugBody(body);
            return body;
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection conn;
        if ("https".equalsIgnoreCase(url.getProtocol())) {
            HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
            https.setSSLSocketFactory(trustAllSslContext().getSocketFactory());
            https.setHostnameVerifier((h, s) -> true);
            conn = https;
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", basicAuthHeader());
        return conn;
    }

    private String basicAuthHeader() {
        String creds = config.getUsername() + ":" + config.getPassword();
        return "Basic " + Base64.getEncoder()
                .encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    private String readBody(HttpURLConnection conn) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /** Percent-encodes a query parameter value (replaces the characters that break URLs). */
    private static String encodeQueryParam(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return value; // UTF-8 is always supported
        }
    }

    // -----------------------------------------------------------------------
    // Response parsing — one method per probe
    // -----------------------------------------------------------------------

    /** Internal value object for a ping probe response. */
    static final class PingProbeResult {
        final boolean reachable;
        final long    responseTimeMs;
        PingProbeResult(boolean reachable, long responseTimeMs) {
            this.reachable       = reachable;
            this.responseTimeMs  = responseTimeMs;
        }
    }

    /** Internal value object for a TCP probe response. */
    static final class TcpProbeResult {
        final boolean open;
        final long    responseTimeMs;
        TcpProbeResult(boolean open, long responseTimeMs) {
            this.open            = open;
            this.responseTimeMs  = responseTimeMs;
        }
    }

    /** Internal value object for an HTTP probe response. */
    static final class HttpProbeResult {
        final String  url;
        final int     status;
        final boolean reachable;
        final String  errorMsg;
        HttpProbeResult(String url, int status, boolean reachable, String errorMsg) {
            this.url       = url;
            this.status    = status;
            this.reachable = reachable;
            this.errorMsg  = errorMsg;
        }
    }

    /**
     * Parses the JSON body returned by {@code GET /ping} into a {@link PingProbeResult}.
     *
     * <p>Expected fields: {@code reachable}, {@code response_time}</p>
     */
    PingProbeResult parsePingResponse(String json) {
        if (json == null || json.isEmpty()) {
            return new PingProbeResult(false, -1L);
        }
        java.util.Map<String, String> f = parseJsonStrings(json);
        boolean reachable = "true".equalsIgnoreCase(f.getOrDefault("reachable", "false"));
        long    ms        = parseLong(f.getOrDefault("response_time", "-1"), -1L);
        return new PingProbeResult(reachable, ms);
    }

    /**
     * Parses the JSON body returned by {@code GET /tcp} into a {@link TcpProbeResult}.
     *
     * <p>Expected fields: {@code open}, {@code response_time}</p>
     */
    TcpProbeResult parseTcpResponse(String json) {
        if (json == null || json.isEmpty()) {
            return new TcpProbeResult(false, -1L);
        }
        java.util.Map<String, String> f = parseJsonStrings(json);
        boolean open = "true".equalsIgnoreCase(f.getOrDefault("open", "false"));
        long    ms   = parseLong(f.getOrDefault("response_time", "-1"), -1L);
        return new TcpProbeResult(open, ms);
    }

    /**
     * Parses the JSON body returned by {@code GET /http} into an {@link HttpProbeResult}.
     *
     * <p>Expected fields: {@code url}, {@code http_status}, {@code reachable},
     * {@code error_msg}</p>
     */
    HttpProbeResult parseHttpResponse(String json, String fallbackUrl) {
        if (json == null || json.isEmpty()) {
            return new HttpProbeResult(fallbackUrl, 0, false, "IS returned empty response");
        }
        java.util.Map<String, String> f = parseJsonStrings(json);
        String  url      = f.getOrDefault("url",          fallbackUrl);
        int     status   = parseInt(f.getOrDefault("http_status", "0"), 0);
        boolean reachable= "true".equalsIgnoreCase(f.getOrDefault("reachable", "false"));
        String  errorMsg = f.getOrDefault("error_msg",    "");
        return new HttpProbeResult(url, status, reachable, errorMsg);
    }

    private static java.util.Map<String, String> parseJsonStrings(String json) {
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        Matcher m = JSON_STRING.matcher(json);
        while (m.find()) {
            fields.put(m.group(1), m.group(2));
        }
        return fields;
    }

    private static long parseLong(String s, long defaultValue) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException | NullPointerException e) { return defaultValue; }
    }

    private static int parseInt(String s, int defaultValue) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException | NullPointerException e) { return defaultValue; }
    }

    private static EndpointCheckResult errorResult(String apiName, String apiVersion,
                                                    String aliasName, String url,
                                                    String errorMsg) {
        return new EndpointCheckResult(
                apiName, apiVersion, aliasName, url,
                0, false, errorMsg,
                false, -1L, false, -1L);
    }

    // -----------------------------------------------------------------------
    // Debug helpers
    // -----------------------------------------------------------------------

    private void debugProbeConfig() {
        if (httpDebugConfig.isEnabled()) {
            debugStream.println("[HTTP-DEBUG] IS-Probe: " + config.buildBaseUrl()
                    + "  user=" + config.getUsername());
        }
    }

    private void debugMsg(String msg) {
        if (httpDebugConfig.isEnabled()) {
            debugStream.println(msg);
        }
    }

    private void debugBody(String body) {
        if (httpDebugConfig.shouldIncludeResponseBody()) {
            debugStream.println("[HTTP-DEBUG] IS Body " + body);
        }
    }

    // -----------------------------------------------------------------------
    // Trust-all TLS (for IS instances with self-signed certificates)
    // -----------------------------------------------------------------------

    private static SSLContext trustAllSslContext() {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers()             { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
        };
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, null);
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Could not create trust-all TLS context", e);
        }
    }
}
