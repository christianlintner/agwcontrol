package com.agwcontrol;

public class ServerConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String isUrl;

    public ServerConfig(String host, int port) {
        this(host, port, null, null, null);
    }

    public ServerConfig(String host, int port, String username, String password) {
        this(host, port, username, password, null);
    }

    public ServerConfig(String host, int port, String username, String password, String isUrl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.isUrl = isUrl;
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
}
