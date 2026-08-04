package com.agwcontrol;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;

public class EndpointCheckService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 15_000;
    private static final int PING_TIMEOUT_MS    = 2_000;
    private static final int TCP_TIMEOUT_MS     = 2_000;

    private final PingService pingService;
    private final TcpCheckService tcpService;

    public EndpointCheckService() {
        this(new PingService(), new TcpCheckService());
    }

    EndpointCheckService(PingService pingService, TcpCheckService tcpService) {
        this.pingService = pingService;
        this.tcpService  = tcpService;
    }

    /**
     * Prüft einen Backend-Endpoint per Ping, TCP-Connect und HTTP(S) HEAD/GET.
     *
     * @param apiName    Name der API
     * @param apiVersion Version der API
     * @param urlStr     aufgelöste Backend-URL (z.B. https://backend:8080/service)
     */
    public EndpointCheckResult check(String apiName, String apiVersion, String urlStr) {
        // Host + Port aus URL extrahieren
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
            return new EndpointCheckResult(apiName, apiVersion, null, urlStr, 0, false,
                    "Ungültige URL: " + e.getMessage(), false, -1L, false, -1L);
        }

        // Ping
        PingResult ping = pingService.ping(host, PING_TIMEOUT_MS);

        // TCP
        TcpCheckResult tcp = tcpService.check(host, port, TCP_TIMEOUT_MS);

        // HTTP
        int httpStatus = 0;
        String errorMsg = "";
        try {
            httpStatus = doRequest(urlStr, "HEAD");
            if (httpStatus == 405) {
                httpStatus = doRequest(urlStr, "GET");
            }
        } catch (Exception e) {
            errorMsg = e.getMessage();
        }
        boolean reachable = httpStatus > 0;

        return new EndpointCheckResult(apiName, apiVersion, null, urlStr,
                httpStatus, reachable, errorMsg,
                ping.isReachable(), ping.getResponseTimeMs(),
                tcp.isOpen(), tcp.getResponseTimeMs());
    }

    /**
     * Prüft einen Backend-Endpoint und speichert das Ergebnis in der DB.
     *
     * @param aliasName  Alias-Name des Endpoints (null wenn direkter Endpoint)
     * @param environment Umgebungsbezeichner (z. B. "PROD")
     * @param apiId      ID der API in der DB
     * @param serverHost Hostname des AGW-Servers, von dem der Check ausgeführt wird
     * @param db         Datenbank, in die das Ergebnis gespeichert wird
     */
    public EndpointCheckResult check(String apiName, String apiVersion,
                                     String aliasName, String urlStr,
                                     String environment, String apiId, String serverHost,
                                     ApiDatabase db) throws SQLException {
        EndpointCheckResult result = check(apiName, apiVersion, urlStr);
        // Ergebnis mit Alias-Name neu verpacken, falls vorhanden
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

    private int doRequest(String urlStr, String method) throws IOException {
        URL url;
        try {
            url = new URI(urlStr).toURL();
        } catch (final URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException(e);
        }
        HttpURLConnection conn;
        if ("https".equalsIgnoreCase(url.getProtocol())) {
            conn = (HttpsURLConnection) url.openConnection();
            ((HttpsURLConnection) conn).setSSLSocketFactory(trustAllSslContext().getSocketFactory());
            ((HttpsURLConnection) conn).setHostnameVerifier((h, s) -> true);
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod(method);
        conn.setInstanceFollowRedirects(true);
        try {
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private SSLContext trustAllSslContext() {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
        };
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, null);
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("TLS-Kontext konnte nicht erstellt werden", e);
        }
    }
}
