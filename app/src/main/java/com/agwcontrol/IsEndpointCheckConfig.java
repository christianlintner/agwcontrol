package com.agwcontrol;

/**
 * Connection settings for the remote webMethods Integration Server that
 * performs the connectivity probes (DNS, ping, TCP, HTTP) on behalf of
 * this client.
 *
 * <p>When an instance of this class is supplied to {@link IsEndpointCheckService},
 * all endpoint checks are delegated to the IS REST endpoint
 * {@code GET /rest/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD/check?url=…}
 * instead of being executed locally.</p>
 *
 * <p>The IS server is typically the same AGW/IS instance whose APIs are being
 * managed, so it can reach backend endpoints that the local client machine
 * cannot (e.g. internal corporate network segments).</p>
 */
public class IsEndpointCheckConfig {

    /** Default IS HTTP port (plain). */
    public static final int DEFAULT_PORT_HTTP  = 5555;

    /** Default IS HTTPS port. */
    public static final int DEFAULT_PORT_HTTPS = 5443;

    /** Base path of the REST API Descriptor on IS. */
    public static final String RAD_BASE_PATH =
            "/rest/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD";

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
     * Convenience constructor using HTTP and default port 5555.
     */
    public IsEndpointCheckConfig(String host, String username, String password) {
        this("http", host, DEFAULT_PORT_HTTP, username, password);
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
     * Builds the base URL for the RAD, e.g.
     * {@code http://localhost:5555/rest/at.oebb.infra.pro.agwctl.pub.rs.v1.checkRAD}.
     */
    public String buildBaseUrl() {
        return scheme + "://" + host + ":" + port + RAD_BASE_PATH;
    }

    @Override
    public String toString() {
        return scheme + "://" + host + ":" + port + RAD_BASE_PATH;
    }
}
