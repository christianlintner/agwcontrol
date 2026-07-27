package com.agwcontrol;

public class TcpCheckResult {

    private final String host;
    private final int port;
    private final boolean open;
    private final long responseTimeMs;

    public TcpCheckResult(String host, int port, boolean open, long responseTimeMs) {
        this.host = host;
        this.port = port;
        this.open = open;
        this.responseTimeMs = responseTimeMs;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isOpen() {
        return open;
    }

    /** Verbindungszeit in ms, oder -1 wenn nicht erreichbar. */
    public long getResponseTimeMs() {
        return responseTimeMs;
    }
}
