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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgwApiService {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    /** Ruft GET /rest/apigateway/apis/{apiId} auf und gibt die Gateway-Endpoint-URLs zurück. */
    public List<String> getEndpoints(ServerConfig server, String apiId) throws IOException {
        String baseUrl = resolveBaseUrl(server);
        URL url = new URL(baseUrl + "/rest/apigateway/apis/" + apiId);

        HttpURLConnection conn = openConnection(url, server);
        try {
            int status = conn.getResponseCode();
            if (status == 401) {
                throw new IOException("Authentifizierung fehlgeschlagen (HTTP 401) für " + baseUrl);
            }
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " von " + baseUrl);
            }
            return parseEndpoints(readBody(conn));
        } finally {
            conn.disconnect();
        }
    }

    /** Ruft GET /rest/apigateway/apis auf und gibt die gefundenen APIs zurück. */
    public List<ApiInfo> listApis(ServerConfig server) throws IOException {
        String baseUrl = resolveBaseUrl(server);
        URL url = new URL(baseUrl + "/rest/apigateway/apis?size=750");

        HttpURLConnection conn = openConnection(url, server);
        try {
            int status = conn.getResponseCode();
            if (status == 401) {
                throw new IOException("Authentifizierung fehlgeschlagen (HTTP 401) für " + baseUrl);
            }
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " von " + baseUrl);
            }

            String body = readBody(conn);
            return parseApis(body);
        } finally {
            conn.disconnect();
        }
    }

    /** IS-URL hat Vorrang; fehlt sie, wird https://<host>:<port> verwendet. */
    String resolveBaseUrl(ServerConfig server) {
        String isUrl = server.getIsUrl();
        if (isUrl != null && !isUrl.isEmpty()) {
            // Trailing slash entfernen
            return isUrl.endsWith("/") ? isUrl.substring(0, isUrl.length() - 1) : isUrl;
        }
        return "https://" + server.getHost() + ":" + server.getPort();
    }

    private HttpURLConnection openConnection(URL url, ServerConfig server) throws IOException {
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
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", basicAuthHeader(server));
        return conn;
    }

    private String basicAuthHeader(ServerConfig server) {
        String creds = server.getUsername() + ":" + server.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
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

    // ---------------------------------------------------------------
    // Minimales JSON-Parsing ohne externe Bibliothek.
    // ---------------------------------------------------------------

    // Extrahiert den Inhalt von "gatewayEndPoints":["url1","url2",...]
    private static final Pattern ENDPOINTS_ARRAY_PATTERN =
            Pattern.compile("\"gatewayEndPoints\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.DOTALL);

    private static final Pattern QUOTED_STRING_PATTERN =
            Pattern.compile("\"([^\"]+)\"");

    List<String> parseEndpoints(String json) {
        List<String> result = new ArrayList<>();
        Matcher arrayMatcher = ENDPOINTS_ARRAY_PATTERN.matcher(json);
        if (arrayMatcher.find()) {
            Matcher strMatcher = QUOTED_STRING_PATTERN.matcher(arrayMatcher.group(1));
            while (strMatcher.find()) {
                result.add(strMatcher.group(1));
            }
        }
        return result;
    }

    // Erwartet: {"apiResponse":[{"api":{...},"responseStatus":"SUCCESS"},...]}

    // Trifft auf jeden "api":{...}-Block zu (nicht-greedy bis zur passenden })
    private static final Pattern API_BLOCK_PATTERN =
            Pattern.compile("\"api\"\\s*:\\s*\\{(.*?)\\}\\s*,?\\s*\"responseStatus\"",
                    Pattern.DOTALL);

    List<ApiInfo> parseApis(String json) {
        List<ApiInfo> result = new ArrayList<>();
        Matcher blockMatcher = API_BLOCK_PATTERN.matcher(json);
        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            String id      = extractString(block, "id");
            String name    = extractString(block, "apiName");
            String version = extractString(block, "apiVersion");
            String type    = extractString(block, "type");
            boolean active = extractBoolean(block, "isActive");
            result.add(new ApiInfo(id, name, version, type, active));
        }
        return result;
    }

    private String extractString(String block, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(block);
        return m.find() ? m.group(1) : "";
    }

    private boolean extractBoolean(String block, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(block);
        return m.find() && "true".equals(m.group(1));
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
