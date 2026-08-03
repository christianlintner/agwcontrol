package com.agwcontrol;

public class TcpCheckResult {

    private final String host;
    private final int port;
    private final String label;
    private final boolean open;
    private final long responseTimeMs;

    public TcpCheckResult(String host, int port, boolean open, long responseTimeMs) {
        this(host, port, null, open, responseTimeMs);
    }

    public TcpCheckResult(String host, int port, String label, boolean open, long responseTimeMs) {
        this.host = host;
        this.port = port;
        this.label = label;
        this.open = open;
        this.responseTimeMs = responseTimeMs;
    }

    public TcpCheckResult withLabel(String label) {
        return new TcpCheckResult(this.host, this.port, label, this.open, this.responseTimeMs);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /** Label zur Kennzeichnung des Adresstyps: "AGW", "IS", "CLUSTER", "CLUSTER-CERT" oder null. */
    public String getLabel() {
        return label;
    }

    public boolean isOpen() {
        return open;
    }

    /** Verbindungszeit in ms, oder -1 wenn nicht erreichbar. */
    public long getResponseTimeMs() {
        return responseTimeMs;
    }
}
