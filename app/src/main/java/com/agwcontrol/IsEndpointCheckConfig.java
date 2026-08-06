package com.agwcontrol;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Connection settings for the remote webMethods Integration Server that
 * performs the connectivity probes (DNS, ping, TCP, HTTP) on behalf of
 * this client.
 *
 * <p>When an instance of this class is supplied to {@link IsEndpointCheckService},
 * all endpoint checks are delegated to the IS native Invoke endpoint, e.g.:
 * <pre>
 *   GET {IS-URL}/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services/checkAll?url=…
 * </pre>
 * instead of being executed locally.</p>
 *
 * <p>The IS-URL and credentials are read exclusively from the KeePass custom
 * field {@code IS-URL} and the standard Username/Password fields of the
 * respective server entry.</p>
 */
public class IsEndpointCheckConfig {

    /** Default IS HTTP port (plain). */
    public static final int DEFAULT_PORT_HTTP  = 5555;

    /** Default IS HTTPS port. */
    public static final int DEFAULT_PORT_HTTPS = 5443;

    /** Base path of the IS native Invoke endpoint for the checkRAD services. */
    public static final String RAD_BASE_PATH =
            "/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services";

    private final String scheme;
    private final String host;
    private final int    port;
    private final String username;
    private final String password;

    /**
     * Creates a configuration with explicit credentials.
     *
     * @param scheme   URL scheme — {@code "http"} or {@code "https"}
     * @param host     Hostname or IP address of the IS instance
     * @param port     Port number (e.g. 5555 or 5443)
     * @param username IS user with Administrators role
     * @param password Password for the IS user
     */
    public IsEndpointCheckConfig(String scheme, String host, int port,
                                 String username, String password) {
        if (scheme == null || scheme.isEmpty()) throw new IllegalArgumentException("scheme required");
        if (host   == null || host.isEmpty())   throw new IllegalArgumentException("host required");
        if (port   <= 0 || port > 65535)        throw new IllegalArgumentException("invalid port: " + port);
        this.scheme   = scheme;
        this.host     = host;
        this.port     = port;
        this.username = username;
        this.password = password;
    }

    /**
     * Creates a configuration from a fully-qualified IS-URL string (e.g.
     * {@code https://vm40757.linux.oebb.at:5559}). The scheme, host and port
     * are parsed from {@code isUrl}; username and password come from the
     * standard KeePass fields of the server entry.
     *
     * @param isUrl    Full IS base URL including scheme and port
     * @param username IS user with Administrators role
     * @param password Password for the IS user
     * @throws IllegalArgumentException if {@code isUrl} cannot be parsed or
     *                                  is missing scheme/host/port
     */
    public IsEndpointCheckConfig(String isUrl, String username, String password) {
        if (isUrl == null || isUrl.isEmpty()) throw new IllegalArgumentException("isUrl required");
        try {
            URI uri = new URI(isUrl);
            String parsedScheme = uri.getScheme();
            String parsedHost   = uri.getHost();
            int    parsedPort   = uri.getPort();
            if (parsedScheme == null || parsedScheme.isEmpty())
                throw new IllegalArgumentException("isUrl missing scheme: " + isUrl);
            if (parsedHost == null || parsedHost.isEmpty())
                throw new IllegalArgumentException("isUrl missing host: " + isUrl);
            if (parsedPort <= 0)
                throw new IllegalArgumentException("isUrl missing or invalid port: " + isUrl);
            this.scheme   = parsedScheme;
            this.host     = parsedHost;
            this.port     = parsedPort;
            this.username = username;
            this.password = password;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("isUrl is not a valid URI: " + isUrl, e);
        }
    }


    /** URL scheme: {@code "http"} or {@code "https"}. */
    public String getScheme()   { return scheme; }

    /** Hostname or IP address of the IS instance. */
    public String getHost()     { return host; }

    /** Port number of the IS instance. */
    public int    getPort()     { return port; }

    /** IS username (must be in the Administrators group). */
    public String getUsername() { return username; }

    /** IS password. */
    public String getPassword() { return password; }

    /**
     * Builds the base URL for the IS Invoke endpoint, e.g.
     * {@code https://vm40757.linux.oebb.at:5559/invoke/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD_.services}.
     */
    public String buildBaseUrl() {
        return scheme + "://" + host + ":" + port + RAD_BASE_PATH;
    }

    @Override
    public String toString() {
        return scheme + "://" + host + ":" + port + RAD_BASE_PATH;
    }
}
