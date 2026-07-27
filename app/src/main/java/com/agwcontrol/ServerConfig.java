package com.agwcontrol;

public class ServerConfig {

    private final String host;
    private final int port;

    public ServerConfig(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
