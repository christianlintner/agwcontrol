package com.agwcontrol;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
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
 * Server instance via the native IS Invoke endpoint.
 *
 * <p>Instead of performing DNS resolution, ICMP ping, TCP connect, and HTTP GET
 * locally on the client machine, this service calls the IS Invoke services
 * from the {@code at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services} package:
 * <pre>
 *   GET {IS-URL}/invoke/.../resolveHost?url=…
 *   GET {IS-URL}/invoke/.../checkPing?host=…
 *   GET {IS-URL}/invoke/.../checkTcp?host=…&amp;port=…
 *   GET {IS-URL}/invoke/.../checkHttp?url=…
 *   GET {IS-URL}/invoke/.../checkAll?url=…
 * </pre>
 * The IS-URL and credentials are read from the KeePass custom field {@code IS-URL}
 * and standard Username/Password fields (see {@link IsEndpointCheckConfig}).</p>
 *
 * <p>Authentication uses HTTP Basic Auth. The IS user must be a member of the
 * {@code Administrators} group.</p>
 */
public class IsEndpointCheckService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    private static final Pattern JSON_STRING =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    private final IsEndpointCheckConfig config;

    public IsEndpointCheckService(IsEndpointCheckConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        this.config = config;
    }

    // -----------------------------------------------------------------------
    // Public API — mirrors EndpointCheckService
    // -----------------------------------------------------------------------

    /**
     * Runs the full endpoint check remotely and returns an {@link EndpointCheckResult}.
     *
     * <p>Calls {@code GET /check?url={urlStr}} on the configured IS instance.
     * All four probes (DNS, ping, TCP, HTTP) are performed by IS from its own
     * network location.</p>
     *
     * @param apiName    Name of the API being checked (for result labelling only)
     * @param apiVersion Version of the API (for result labelling only)
     * @param urlStr     Backend endpoint URL to probe
     * @return populated {@link EndpointCheckResult}; never {@code null}
     */
    public EndpointCheckResult check(String apiName, String apiVersion, String urlStr) {
        try {
            String json = callCheckEndpoint(urlStr);
            return parseCheckResponse(apiName, apiVersion, null, urlStr, json);
        } catch (IOException e) {
            return errorResult(apiName, apiVersion, null, urlStr,
                    "IS probe unreachable: " + e.getMessage());
        }
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
        EndpointCheckResult result;
        try {
            String json = callCheckEndpoint(urlStr);
            result = parseCheckResponse(apiName, apiVersion, aliasName, urlStr, json);
        } catch (IOException e) {
            result = errorResult(apiName, apiVersion, aliasName, urlStr,
                    "IS probe unreachable: " + e.getMessage());
        }
        db.saveCheckResult(environment, apiId, serverHost, result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Single-probe public API
    // -----------------------------------------------------------------------

    /**
     * Resolves a hostname via DNS on the IS instance.
     *
     * @param endpointUrl fully qualified URL whose hostname shall be resolved
     * @return {@link PingResult} carrying the resolved host; reachable=true when
     *         resolution succeeded, response_time=-1 (not applicable for DNS)
     */
    public PingResult resolveHost(String endpointUrl) {
        try {
            String json   = callInvoke("resolveHost", "url=" + encodeQueryParam(endpointUrl));
            java.util.Map<String, String> f = parseJsonFields(json);
            String host   = f.getOrDefault("host", endpointUrl);
            boolean ok    = host != null && !host.isEmpty()
                            && !f.getOrDefault("resolved_ip", "").isEmpty();
            return new PingResult(host, ok, -1L);
        } catch (IOException e) {
            return new PingResult(endpointUrl, false, -1L);
        }
    }

    /**
     * Tests ICMP reachability via the IS instance.
     *
     * @param host hostname or IP to ping
     * @return {@link PingResult} with reachability and round-trip time
     */
    public PingResult checkPing(String host) {
        try {
            String json = callInvoke("checkPing", "host=" + encodeQueryParam(host));
            java.util.Map<String, String> f = parseJsonFields(json);
            boolean reachable = "true".equalsIgnoreCase(f.getOrDefault("reachable", "false"));
            long    ms        = parseLong(f.getOrDefault("response_time", "-1"), -1L);
            return new PingResult(f.getOrDefault("host", host), reachable, ms);
        } catch (IOException e) {
            return new PingResult(host, false, -1L);
        }
    }

    /**
     * Tests TCP connectivity via the IS instance.
     *
     * @param host hostname or IP to connect to
     * @param port TCP port number
     * @return {@link TcpCheckResult} with open flag and connection time
     */
    public TcpCheckResult checkTcp(String host, int port) {
        try {
            String query = "host=" + encodeQueryParam(host) + "&port=" + port;
            String json  = callInvoke("checkTcp", query);
            java.util.Map<String, String> f = parseJsonFields(json);
            boolean open = "true".equalsIgnoreCase(f.getOrDefault("open", "false"));
            long    ms   = parseLong(f.getOrDefault("response_time", "-1"), -1L);
            return new TcpCheckResult(f.getOrDefault("host", host), port, open, ms);
        } catch (IOException e) {
            return new TcpCheckResult(host, port, false, -1L);
        }
    }

    /**
     * Performs an HTTP(S) GET via the IS instance.
     *
     * @param endpointUrl URL to request
     * @return {@link EndpointCheckResult} with HTTP status and reachability;
     *         ping/TCP fields are set to {@code false}/{@code -1}
     */
    public EndpointCheckResult checkHttp(String endpointUrl) {
        try {
            String json = callInvoke("checkHttp", "url=" + encodeQueryParam(endpointUrl));
            java.util.Map<String, String> f = parseJsonFields(json);
            int     status   = parseInt(f.getOrDefault("http_status", "0"), 0);
            boolean reachable= "true".equalsIgnoreCase(f.getOrDefault("reachable", "false"));
            String  errMsg   = f.getOrDefault("error_msg", "");
            return new EndpointCheckResult(
                    null, null, null, f.getOrDefault("url", endpointUrl),
                    status, reachable, errMsg,
                    false, -1L, false, -1L);
        } catch (IOException e) {
            return new EndpointCheckResult(
                    null, null, null, endpointUrl,
                    0, false, "IS probe unreachable: " + e.getMessage(),
                    false, -1L, false, -1L);
        }
    }

    // -----------------------------------------------------------------------
    // HTTP call
    // -----------------------------------------------------------------------

    /**
     * Calls {@code GET /checkAll?url={encodedUrl}} on the IS Invoke endpoint
     * and returns the raw JSON response body.
     */
    private String callCheckEndpoint(String endpointUrl) throws IOException {
        return callInvoke("checkAll", "url=" + encodeQueryParam(endpointUrl));
    }

    /**
     * Generic Invoke call: {@code GET {buildBaseUrl()}/{operation}?{queryString}}.
     *
     * @param operation   IS service name, e.g. {@code "checkAll"}, {@code "checkPing"}
     * @param queryString pre-encoded query string, e.g. {@code "url=https%3A%2F%2F..."}
     */
    private String callInvoke(String operation, String queryString) throws IOException {
        String fullUrl = config.buildBaseUrl() + "/" + operation + "?" + queryString;
        URL url = new URL(fullUrl);

        HttpURLConnection conn = openConnection(url);
        try {
            int status = conn.getResponseCode();
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
            return readBody(conn);
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
        // Use java.net.URLEncoder but swap '+' back to '%20'
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return value; // UTF-8 is always supported
        }
    }

    // -----------------------------------------------------------------------
    // Response parsing
    // -----------------------------------------------------------------------

    /**
     * Parses the JSON body returned by {@code GET /check} into an
     * {@link EndpointCheckResult}.
     *
     * <p>Expected JSON fields (all strings, matching the IS service output signature):
     * <pre>
     * {
     *   "url":               "https://...",
     *   "host":              "vm10477.org.oebb.at",
     *   "resolved_ip":       "10.66.24.18",
     *   "ping_reachable":    "true",
     *   "ping_response_time":"12",
     *   "tcp_port":          "443",
     *   "tcp_open":          "true",
     *   "tcp_response_time": "8",
     *   "http_status":       "401",
     *   "http_reachable":    "true",
     *   "http_error_msg":    ""
     * }
     * </pre>
     */
    /**
     * Parses all {@code "key":"value"} string pairs from a JSON object into a map.
     * Returns an empty map for null or empty input.
     */
    private static java.util.Map<String, String> parseJsonFields(String json) {
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        if (json == null || json.isEmpty()) return fields;
        Matcher m = JSON_STRING.matcher(json);
        while (m.find()) {
            fields.put(m.group(1), m.group(2));
        }
        return fields;
    }

    EndpointCheckResult parseCheckResponse(String apiName, String apiVersion,
                                           String aliasName, String fallbackUrl,
                                           String json) {
        if (json == null || json.isEmpty()) {
            return errorResult(apiName, apiVersion, aliasName, fallbackUrl,
                    "IS returned empty response");
        }

        java.util.Map<String, String> fields = parseJsonFields(json);

        String url          = fields.getOrDefault("url",               fallbackUrl);
        String pingReachable= fields.getOrDefault("ping_reachable",    "false");
        String pingTime     = fields.getOrDefault("ping_response_time","-1");
        String tcpOpen      = fields.getOrDefault("tcp_open",          "false");
        String tcpTime      = fields.getOrDefault("tcp_response_time", "-1");
        String httpStatus   = fields.getOrDefault("http_status",       "0");
        String httpReachable= fields.getOrDefault("http_reachable",    "false");
        String httpErrorMsg = fields.getOrDefault("http_error_msg",    "");

        boolean pingOk   = "true".equalsIgnoreCase(pingReachable);
        long    pingMs   = parseLong(pingTime, -1L);
        boolean tcpOk    = "true".equalsIgnoreCase(tcpOpen);
        long    tcpMs    = parseLong(tcpTime,  -1L);
        int     status   = parseInt(httpStatus, 0);
        boolean reachable= "true".equalsIgnoreCase(httpReachable);

        return new EndpointCheckResult(
                apiName, apiVersion, aliasName, url,
                status, reachable, httpErrorMsg,
                pingOk, pingMs, tcpOk, tcpMs);
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
