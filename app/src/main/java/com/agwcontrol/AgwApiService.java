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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgwApiService {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    /**
     * Ruft GET /rest/apigateway/apis/{apiId} auf, liest das nativeEndpoint-Array
     * und gibt die aufgelösten Routing-Endpoints zurück.
     * Bei alias=true wird der Alias via GET /alias/{name} aufgelöst.
     */
    public List<RoutingEndpoint> getNativeEndpoints(ServerConfig server, String apiId) throws IOException {
        String baseUrl = resolveBaseUrl(server);
        URL url = new URL(baseUrl + "/rest/apigateway/apis/" + apiId);

        HttpURLConnection conn = openConnection(url, server);
        String body;
        try {
            int status = conn.getResponseCode();
            if (status == 401) {
                throw new IOException("Authentifizierung fehlgeschlagen (HTTP 401) für " + baseUrl);
            }
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " von " + baseUrl);
            }
            body = readBody(conn);
        } finally {
            conn.disconnect();
        }

        List<RoutingEndpoint> result = new ArrayList<>();
        for (NativeEndpointEntry entry : parseNativeEndpoints(body)) {
            if (entry.alias) {
                String resolvedUrl = resolveAlias(server, entry.uri);
                if (resolvedUrl != null) {
                    result.add(RoutingEndpoint.alias(entry.uri, resolvedUrl));
                } else {
                    // Alias nicht auflösbar – Alias-Namen als URL-Platzhalter
                    result.add(RoutingEndpoint.alias(entry.uri, null));
                }
            } else {
                result.add(RoutingEndpoint.direct(entry.uri));
            }
        }
        return result;
    }

    /**
     * Löst einen Endpoint-Alias via GET /rest/apigateway/alias/{aliasName} auf.
     * Gibt die endPointURI zurück oder null wenn nicht gefunden / kein endpoint-Typ.
     */
    String resolveAlias(ServerConfig server, String aliasName) throws IOException {
        String baseUrl = resolveBaseUrl(server);
        URL url = new URL(baseUrl + "/rest/apigateway/alias/" + aliasName);

        HttpURLConnection conn = openConnection(url, server);
        try {
            int status = conn.getResponseCode();
            if (status == 404) {
                return null;
            }
            if (status < 200 || status >= 300) {
                return null;
            }
            return parseEndPointURI(readBody(conn));
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Lädt die API-Liste – mit DB-Cache-Unterstützung.
     *
     * <p>Wenn {@code cache.isUseDbForApis()} true ist und die DB Daten enthält,
     * werden diese zurückgegeben. Andernfalls wird vom Server geladen und
     * die DB überschrieben. Das {@code cacheHint}-Array (Länge 1) wird mit
     * {@code "DB"} oder {@code "Server"} befüllt, damit der Aufrufer eine
     * Statusmeldung ausgeben kann.
     *
     * @param cacheHint Optional: String-Array der Länge 1, wird mit Quelle befüllt.
     *                  Darf {@code null} sein.
     */
    public List<ApiInfo> listApis(ServerConfig server, String environment,
                                   ApiDatabase db, DbCacheConfig cache,
                                   String[] cacheHint) throws IOException {
        if (cache.isUseDbForApis()) {
            try {
                List<ApiInfo> cached = db.loadApis(environment);
                if (!cached.isEmpty()) {
                    if (cacheHint != null) cacheHint[0] = "DB";
                    return cached;
                }
            } catch (SQLException e) {
                // DB-Fehler: Fallback auf Server
            }
            if (cacheHint != null) cacheHint[0] = "Cache leer – lade vom Server";
        } else {
            if (cacheHint != null) cacheHint[0] = "Server";
        }
        List<ApiInfo> result = listApis(server);
        try {
            db.saveApis(environment, result);
        } catch (SQLException e) {
            // Speicherfehler ignorieren – Ergebnis trotzdem zurückgeben
        }
        return result;
    }

    /**
     * Lädt native Endpoints – mit DB-Cache-Unterstützung.
     *
     * @param cacheHint Optional: String-Array der Länge 1, wird mit Quelle befüllt.
     *                  Darf {@code null} sein.
     */
    public List<RoutingEndpoint> getNativeEndpoints(ServerConfig server, String apiId,
                                                     String environment,
                                                     ApiDatabase db, DbCacheConfig cache,
                                                     String[] cacheHint) throws IOException {
        if (cache.isUseDbForEndpoints()) {
            try {
                List<RoutingEndpoint> cached = db.loadEndpoints(environment, apiId);
                if (!cached.isEmpty()) {
                    if (cacheHint != null) cacheHint[0] = "DB";
                    return cached;
                }
            } catch (SQLException e) {
                // DB-Fehler: Fallback auf Server
            }
            if (cacheHint != null) cacheHint[0] = "Cache leer – lade vom Server";
        } else {
            if (cacheHint != null) cacheHint[0] = "Server";
        }
        List<RoutingEndpoint> result = getNativeEndpoints(server, apiId);
        try {
            db.saveEndpoints(environment, apiId, result);
        } catch (SQLException e) {
            // Speicherfehler ignorieren
        }
        return result;
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

    /** Internes DTO für einen nativeEndpoint-Eintrag. */
    static final class NativeEndpointEntry {
        final String uri;
        final boolean alias;
        NativeEndpointEntry(String uri, boolean alias) {
            this.uri = uri;
            this.alias = alias;
        }
    }

    // Trifft auf jeden nativeEndpoint-Block innerhalb von {...}
    private static final Pattern NATIVE_EP_BLOCK_PATTERN =
            Pattern.compile("\\{([^{}]*\"uri\"[^{}]*)\\}", Pattern.DOTALL);

    // Extrahiert "endPointURI":"<value>"
    private static final Pattern ENDPOINT_URI_PATTERN =
            Pattern.compile("\"endPointURI\"\\s*:\\s*\"([^\"]+)\"");

    List<NativeEndpointEntry> parseNativeEndpoints(String json) {
        List<NativeEndpointEntry> result = new ArrayList<>();
        // Nur den nativeEndpoint-Array-Bereich betrachten
        Pattern nativeArrayPattern = Pattern.compile(
                "\"nativeEndpoint\"\\s*:\\s*\\[(.*?)\\](?=\\s*[,}])", Pattern.DOTALL);
        Matcher arrayMatcher = nativeArrayPattern.matcher(json);
        if (!arrayMatcher.find()) {
            return result;
        }
        String arrayContent = arrayMatcher.group(1);
        Matcher blockMatcher = NATIVE_EP_BLOCK_PATTERN.matcher(arrayContent);
        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            String uri = extractString(block, "uri");
            boolean alias = extractBoolean(block, "alias");
            if (uri != null && !uri.isEmpty()) {
                result.add(new NativeEndpointEntry(uri, alias));
            }
        }
        return result;
    }

    String parseEndPointURI(String json) {
        Matcher m = ENDPOINT_URI_PATTERN.matcher(json);
        return m.find() ? m.group(1) : null;
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
