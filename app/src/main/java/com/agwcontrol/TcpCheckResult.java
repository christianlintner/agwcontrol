package com.agwcontrol;

public class TcpCheckResult {

    private final String host;
    private final int port;
    private final String label;
    private final boolean open;
    private final long responseTimeMs;
    private final boolean configured;

    public TcpCheckResult(String host, int port, boolean open, long responseTimeMs) {
        this(host, port, null, open, responseTimeMs, true);
    }

    public TcpCheckResult(String host, int port, String label, boolean open, long responseTimeMs) {
        this(host, port, label, open, responseTimeMs, true);
    }

    private TcpCheckResult(String host, int port, String label, boolean open, long responseTimeMs, boolean configured) {
        this.host = host;
        this.port = port;
        this.label = label;
        this.open = open;
        this.responseTimeMs = responseTimeMs;
        this.configured = configured;
    }

    public static TcpCheckResult notConfigured(String label) {
        return new TcpCheckResult("-", 0, label, false, -1, false);
    }

    public TcpCheckResult withLabel(String label) {
        return new TcpCheckResult(this.host, this.port, label, this.open, this.responseTimeMs, this.configured);
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

    public boolean isConfigured() {
        return configured;
    }

    /** Verbindungszeit in ms, oder -1 wenn nicht erreichbar. */
    public long getResponseTimeMs() {
        return responseTimeMs;
    }
}
