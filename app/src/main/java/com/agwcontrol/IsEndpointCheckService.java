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
 * <p>Instead of performing DNS resolution, ICMP ping, TCP connect, and HTTP GET
 * locally on the client machine, this service calls:
 * <pre>
 *   GET {IS-base}/check?url={endpointUrl}
 * </pre>
 * The IS server performs all four probes from its own network location and
 * returns a combined JSON response. This is the intended production mode when
 * the client machine cannot directly reach the backend endpoints (e.g. it sits
 * outside the corporate network while the IS instance is inside).</p>
 *
 * <p>The service implements the same public interface as the local
 * {@link EndpointCheckService} so that call sites in {@link InteractiveMenu}
 * require minimal changes.</p>
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
        debugProbeConfig();
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
        debugProbeConfig();
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
    // HTTP call
    // -----------------------------------------------------------------------

    /**
     * Calls {@code GET /check?url={encodedUrl}} on the IS RAD and returns the raw
     * JSON response body.
     */
    private String callCheckEndpoint(String endpointUrl) throws IOException {
        String encodedUrl = encodeQueryParam(endpointUrl);
        String fullUrl    = config.buildBaseUrl() + "/check?url=" + encodedUrl;
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
    EndpointCheckResult parseCheckResponse(String apiName, String apiVersion,
                                           String aliasName, String fallbackUrl,
                                           String json) {
        if (json == null || json.isEmpty()) {
            return errorResult(apiName, apiVersion, aliasName, fallbackUrl,
                    "IS returned empty response");
        }

        java.util.Map<String, String> fields = new java.util.HashMap<>();
        Matcher m = JSON_STRING.matcher(json);
        while (m.find()) {
            fields.put(m.group(1), m.group(2));
        }

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
