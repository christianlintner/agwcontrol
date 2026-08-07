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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgwApiService {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    private final HttpDebugConfig httpDebugConfig;
    private final PrintStream debugOut;

    /**
     * In-memory Cache für Endpoint-Aliases pro Server.
     * Key: baseUrl (z. B. "https://vm40757:5559")
     * Value: Map von aliasName → endPointURI
     * Wird beim ersten resolveAlias()-Aufruf pro Server befüllt.
     */
    private final java.util.Map<String, java.util.Map<String, String>> aliasCache =
            new java.util.HashMap<>();

    public AgwApiService() {
        this(new HttpDebugConfig(), System.out);
    }

    public AgwApiService(HttpDebugConfig httpDebugConfig, PrintStream debugOut) {
        this.httpDebugConfig = httpDebugConfig;
        this.debugOut = debugOut;
    }

    /** Ruft GET /rest/apigateway/apis/{apiId} auf und gibt den Body zurück. */
    String fetchApiBody(ServerConfig server, String apiId) throws IOException {
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
            return readBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Ruft GET /rest/apigateway/apis/{apiId} auf, ermittelt den Endpoint
     * über die straightThroughRouting-Policy und löst den Alias auf.
     */
    public List<RoutingEndpoint> getNativeEndpoints(ServerConfig server, String apiId) throws IOException {
        String body = fetchApiBody(server, apiId);

        // Stufe 1: Policy-IDs aus dem API-Body lesen
        List<String> policyIds = parsePolicies(body);
        if (policyIds.isEmpty()) {
            return new ArrayList<>();
        }
        // Stufe 2: Pro Policy die enforcementObjectIds (= Policy Action IDs) sammeln
        List<String> actionIds = new ArrayList<>();
        for (String policyId : policyIds) {
            String policyJson = fetchPolicy(server, policyId);
            if (policyJson != null) {
                actionIds.addAll(parseEnforcementObjectIds(policyJson));
            }
        }
        if (actionIds.isEmpty()) {
            return new ArrayList<>();
        }
        // Stufe 3: Policy Actions per Bulk-Call abrufen und endpointUri extrahieren
        String policyActionsJson = fetchPolicyActions(server, actionIds);
        if (policyActionsJson == null) {
            return new ArrayList<>();
        }
        String endpointUri = parseRoutingEndpointUri(policyActionsJson);
        if (endpointUri == null) {
            return new ArrayList<>();
        }
        List<RoutingEndpoint> result = new ArrayList<>();
        // Wenn der Ausdruck mit ${AliasName} beginnt → Alias auflösen
        // Sonst → direkte URL (Basis-Teil vor erstem ${ oder dem ganzen Wert)
        String aliasName = parseRoutingAliasName(policyActionsJson);
        if (aliasName != null) {
            String resolvedUrl = resolveAlias(server, aliasName);
            result.add(RoutingEndpoint.alias(aliasName, resolvedUrl));
        } else {
            // Direkte URL: alles vor dem ersten "${" abschneiden (falls vorhanden)
            int dollarIdx = endpointUri.indexOf("${");
            String directUrl = dollarIdx >= 0 ? endpointUri.substring(0, dollarIdx) : endpointUri;
            result.add(RoutingEndpoint.direct(directUrl));
        }
        return result;
    }

    /**
     * Ruft GET /rest/apigateway/policies/{policyId} auf und gibt den Body zurück.
     * Bei 404 oder Fehler wird null zurückgegeben.
     */
    String fetchPolicy(ServerConfig server, String policyId) throws IOException {
        String baseUrl = resolveBaseUrl(server);
        URL url = new URL(baseUrl + "/rest/apigateway/policies/" + policyId);

        HttpURLConnection conn = openConnection(url, server);
        try {
            int status = conn.getResponseCode();
            if (status == 404) {
                return null;
            }
            if (status < 200 || status >= 300) {
                return null;
            }
            return readBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Ruft GET /rest/apigateway/policyActions?policyActionIds=... auf und gibt den Body zurück.
     * Bei leerem IDs-Parameter oder Fehler wird null zurückgegeben.
     */
    String fetchPolicyActions(ServerConfig server, List<String> policyActionIds) throws IOException {
        if (policyActionIds == null || policyActionIds.isEmpty()) {
            return null;
        }
        String ids = String.join(",", policyActionIds);
        String baseUrl = resolveBaseUrl(server);
        URL url = new URL(baseUrl + "/rest/apigateway/policyActions?policyActionIds=" + ids);

        HttpURLConnection conn = openConnection(url, server);
        try {
            int status = conn.getResponseCode();
            if (status == 404) {
                return null;
            }
            if (status < 200 || status >= 300) {
                return null;
            }
            return readBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Löst einen Endpoint-Alias auf.
     * Beim ersten Aufruf pro Server wird GET /rest/apigateway/alias einmalig aufgerufen
     * und alle Aliases in den In-memory Cache geladen.
     * Folgeaufrufe lesen direkt aus dem Cache — kein weiterer HTTP-Call.
     */
    String resolveAlias(ServerConfig server, String aliasName) throws IOException {
        String baseUrl = resolveBaseUrl(server);
        if (!aliasCache.containsKey(baseUrl)) {
            loadAllAliases(server, baseUrl);
        }
        java.util.Map<String, String> cache = aliasCache.get(baseUrl);
        return cache != null ? cache.get(aliasName) : null;
    }

    /**
     * Lädt alle Endpoint-Aliases vom Server und befüllt den aliasCache.
     * Ruft GET /rest/apigateway/alias auf.
     */
    void loadAllAliases(ServerConfig server, String baseUrl) throws IOException {
        URL url = new URL(baseUrl + "/rest/apigateway/alias");
        HttpURLConnection conn = openConnection(url, server);
        String body;
        try {
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                // Bei Fehler leere Map cachen damit kein erneuter Aufruf stattfindet
                aliasCache.put(baseUrl, new java.util.HashMap<>());
                return;
            }
            body = readBody(conn);
        } finally {
            conn.disconnect();
        }
        aliasCache.put(baseUrl, parseAllAliases(body));
    }

    /**
     * Parst alle Alias-Einträge mit endPointURI aus dem Alias-Listen-Body.
     * Gibt eine Map von aliasName → endPointURI zurück.
     */
    java.util.Map<String, String> parseAllAliases(String json) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        if (json == null) {
            return result;
        }
        Pattern blockPattern = Pattern.compile("\\{([^{}]*)\\}", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(json);
        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            Matcher nameMatcher = Pattern.compile(
                    "\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
            Matcher uriMatcher = ENDPOINT_URI_PATTERN.matcher(block);
            if (nameMatcher.find() && uriMatcher.find()) {
                result.put(nameMatcher.group(1), uriMatcher.group(1));
            }
        }
        return result;
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
        return getNativeEndpoints(server, apiId, environment, db, cache, cacheHint, null, null, null);
    }

    /**
     * Lädt native Endpoints – mit DB-Cache-Unterstützung und optionaler DNS-Auflösung via IS.
     *
     * <p>Wenn {@code isConfig} nicht {@code null} ist, wird nach dem Server-Abruf für jeden
     * Endpoint mit gültiger URL der IS-Service {@code resolveHost} aufgerufen und die
     * aufgelöste IP via {@link RoutingEndpoint#setResolvedIp(String)} gesetzt.
     * Debug-Ausgaben erscheinen wenn {@code debugConfig} aktiviert ist.</p>
     *
     * @param cacheHint   Optional: String-Array der Länge 1, wird mit Quelle befüllt.
     * @param isConfig    Optional: IS-Verbindungskonfiguration für DNS-Auflösung. {@code null} = überspringen.
     * @param debugConfig Optional: HTTP-Debug-Konfiguration, wird an den IS-Service weitergereicht.
     * @param debugStream Optional: Ausgabe-Stream für Debug-Meldungen.
     */
    public List<RoutingEndpoint> getNativeEndpoints(ServerConfig server, String apiId,
                                                     String environment,
                                                     ApiDatabase db, DbCacheConfig cache,
                                                     String[] cacheHint,
                                                     IsEndpointCheckConfig isConfig,
                                                     HttpDebugConfig debugConfig,
                                                     java.io.PrintStream debugStream) throws IOException {
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
        // DNS-Auflösung via IS-Service resolveHost
        if (isConfig != null) {
            IsEndpointCheckService resolver = new IsEndpointCheckService(
                    isConfig,
                    debugConfig != null ? debugConfig : new HttpDebugConfig(),
                    debugStream != null ? debugStream : System.out);
            for (RoutingEndpoint ep : result) {
                String url = ep.getResolvedUrl();
                if (url != null && !url.isEmpty()) {
                    try {
                        String json = resolver.callResolveHostEndpoint(url);
                        ep.setResolvedIp(resolver.parseResolveHostResponse(json));
                    } catch (java.io.IOException e) {
                        // DNS-Fehler ignorieren – Listing darf nicht blockiert werden
                    }
                }
            }
        }
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
        debugRequest(url);
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
        debugResponseStatus(conn);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();
        debugResponseBody(body);
        return body;
    }

    private void debugRequest(URL url) {
        if (httpDebugConfig.isEnabled()) {
            debugOut.println("[HTTP-DEBUG] GET " + url);
        }
    }

    private void debugResponseStatus(HttpURLConnection conn) throws IOException {
        if (httpDebugConfig.isEnabled()) {
            debugOut.println("[HTTP-DEBUG] Status " + conn.getResponseCode());
        }
    }

    private void debugResponseBody(String body) {
        if (httpDebugConfig.shouldIncludeResponseBody()) {
            debugOut.println("[HTTP-DEBUG] Body " + body);
        }
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

    // Extrahiert "${AliasName}" aus einem Expression-String
    private static final Pattern ALIAS_EXPRESSION_PATTERN =
            Pattern.compile("\\$\\{([^}]+)\\}");

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

    /**
     * Liest das "policies"-Array aus dem API-Detail-Body und gibt die IDs zurück.
     */
    List<String> parsePolicies(String json) {
        List<String> result = new ArrayList<>();
        Pattern policiesPattern = Pattern.compile(
                "\"policies\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher arrayMatcher = policiesPattern.matcher(json);
        if (!arrayMatcher.find()) {
            return result;
        }
        String arrayContent = arrayMatcher.group(1);
        Matcher idMatcher = Pattern.compile("\"([^\"]+)\"").matcher(arrayContent);
        while (idMatcher.find()) {
            result.add(idMatcher.group(1));
        }
        return result;
    }

    /**
     * Liest alle enforcementObjectIds aus einem Policy-Body.
     * Diese IDs entsprechen den Policy Action IDs.
     * Struktur: policy.policyEnforcements[].enforcements[].enforcementObjectId
     */
    List<String> parseEnforcementObjectIds(String json) {
        List<String> result = new ArrayList<>();
        if (json == null) {
            return result;
        }
        Pattern pattern = Pattern.compile(
                "\"enforcementObjectId\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = pattern.matcher(json);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    /**
     * Sucht im policyActions-Response-Body nach der Action mit
     * templateKey=="straightThroughRouting" und gibt den rohen endpointUri-Wert zurück.
     * Gibt null zurück wenn keine solche Action gefunden wird.
     */
    String parseRoutingEndpointUri(String json) {
        if (json == null) {
            return null;
        }
        Pattern straightThroughPattern = Pattern.compile(
                "\"templateKey\"\\s*:\\s*\"straightThroughRouting\"" +
                ".*?" +
                "\"templateKey\"\\s*:\\s*\"endpointUri\"" +
                ".*?" +
                "\"values\"\\s*:\\s*\\[\\s*\"([^\"]+)\"",
                Pattern.DOTALL);
        Matcher m = straightThroughPattern.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Sucht im policyActions-Response-Body nach der Action mit
     * templateKey=="straightThroughRouting" und extrahiert daraus den Alias-Namen
     * aus dem endpointUri-Parameter (z. B. "${AKOS_API_EndpointAlias}/...").
     * Gibt null zurück wenn keine solche Action gefunden wird oder die URL direkt ist.
     */
    String parseRoutingAliasName(String json) {
        String endpointUri = parseRoutingEndpointUri(json);
        if (endpointUri == null) {
            return null;
        }
        Matcher aliasMatcher = ALIAS_EXPRESSION_PATTERN.matcher(endpointUri);
        if (!aliasMatcher.find()) {
            return null;
        }
        // Nur ein Alias wenn der gesamte Ausdruck mit ${...} beginnt
        int matchStart = aliasMatcher.start();
        return matchStart == 0 ? aliasMatcher.group(1) : null;
    }

    String parseEndPointURI(String json) {
        Matcher m = ENDPOINT_URI_PATTERN.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Sucht im Alias-Listen-Body den Eintrag mit dem gegebenen Namen
     * und gibt dessen endPointURI zurück.
     * Erwartet ein JSON-Array oder -Objekt mit Alias-Einträgen, die jeweils
     * "name", "endPointURI" und optional "type" enthalten.
     */
    String parseEndPointURIByName(String json, String aliasName) {
        if (json == null || aliasName == null) {
            return null;
        }
        // Suche den Block der den gesuchten Namen enthält, dann lese endPointURI daraus
        // Muster: suche "name":"<aliasName>" im selben Objekt-Block wie "endPointURI"
        // Strategie: finde alle {...}-Blöcke (flach, ohne verschachtelte {}) und prüfe jeden
        Pattern blockPattern = Pattern.compile(
                "\\{([^{}]*)\\}", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(json);
        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            // Prüfe ob dieser Block den gesuchten Namen enthält
            Matcher nameMatcher = Pattern.compile(
                    "\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
            if (nameMatcher.find() && aliasName.equals(nameMatcher.group(1))) {
                // Name gefunden – lies endPointURI aus diesem Block
                Matcher uriMatcher = ENDPOINT_URI_PATTERN.matcher(block);
                if (uriMatcher.find()) {
                    return uriMatcher.group(1);
                }
            }
        }
        return null;
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
