package com.agwcontrol;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class EndpointCheckService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    /**
     * Prüft einen Gateway-Endpoint per HTTP HEAD (Fallback: GET bei 405).
     *
     * @param apiName    Name der API – wird 1:1 ins Ergebnis übernommen
     * @param apiVersion Version der API – wird 1:1 ins Ergebnis übernommen
     * @param url        Gateway-Endpoint-URL
     */
    public EndpointCheckResult check(String apiName, String apiVersion, String url) {
        try {
            int status = doRequest(url, "HEAD");
            if (status == 405) {
                status = doRequest(url, "GET");
            }
            boolean reachable = status >= 200 && status < 400;
            return new EndpointCheckResult(apiName, apiVersion, url, status, reachable, "");
        } catch (Exception e) {
            return new EndpointCheckResult(apiName, apiVersion, url, 0, false, e.getMessage());
        }
    }

    private int doRequest(String urlStr, String method) throws IOException {
        URL url = new URL(urlStr);
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
