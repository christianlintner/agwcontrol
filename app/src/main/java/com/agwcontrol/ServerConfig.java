package com.agwcontrol;

public class ServerConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String isUrl;
    private final String clusterUrl;
    private final String clusterCertUrl;

    /**
     * Optional configuration for the remote IS endpoint-probe service.
     * When non-null, {@link IsEndpointCheckService} is used instead of the
     * local {@link EndpointCheckService}.
     */
    private IsEndpointCheckConfig isProbeConfig;

    public ServerConfig(String host, int port) {
        this(host, port, null, null, null, null, null);
    }

    public ServerConfig(String host, int port, String username, String password) {
        this(host, port, username, password, null, null, null);
    }

    public ServerConfig(String host, int port, String username, String password, String isUrl) {
        this(host, port, username, password, isUrl, null, null);
    }

    public ServerConfig(String host, int port, String username, String password,
                        String isUrl, String clusterUrl, String clusterCertUrl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.isUrl = isUrl;
        this.clusterUrl = clusterUrl;
        this.clusterCertUrl = clusterCertUrl;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /** IS-URL: REST-API-Endpunkt des Integration Servers (kann vom AGW-Host abweichen). */
    public String getIsUrl() {
        return isUrl;
    }

    /** CLUSTER-URL: Zentrale Cluster-LB-Adresse (external). */
    public String getClusterUrl() {
        return clusterUrl;
    }

    /** CLUSTER-CERT-URL: Zentrale Cluster-LB-Adresse (internal_cert). */
    public String getClusterCertUrl() {
        return clusterCertUrl;
    }

    /**
     * Returns the IS probe configuration when remote endpoint checking is
     * configured for this server entry, or {@code null} to use local probing.
     */
    public IsEndpointCheckConfig getIsProbeConfig() {
        return isProbeConfig;
    }

    /**
     * Attaches an IS probe configuration to this server entry.
     * Called by {@link KeePassConfigLoader} when IS-PROBE-URL / IS-PROBE-USER /
     * IS-PROBE-PASSWORD custom fields are present, or from CLI overrides.
     */
    public void setIsProbeConfig(IsEndpointCheckConfig isProbeConfig) {
        this.isProbeConfig = isProbeConfig;
    }
}
