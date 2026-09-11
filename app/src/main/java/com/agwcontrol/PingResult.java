package com.agwcontrol;

public class PingResult {

    private final String host;
    private final String label;
    private final boolean reachable;
    private final long responseTimeMs;
    private final boolean configured;

    public PingResult(String host, boolean reachable, long responseTimeMs) {
        this(host, null, reachable, responseTimeMs, true);
    }

    public PingResult(String host, String label, boolean reachable, long responseTimeMs) {
        this(host, label, reachable, responseTimeMs, true);
    }

    private PingResult(String host, String label, boolean reachable, long responseTimeMs, boolean configured) {
        this.host = host;
        this.label = label;
        this.reachable = reachable;
        this.responseTimeMs = responseTimeMs;
        this.configured = configured;
    }

    public static PingResult notConfigured(String label) {
        return new PingResult("-", label, false, -1, false);
    }

    public PingResult withLabel(String label) {
        return new PingResult(this.host, label, this.reachable, this.responseTimeMs, this.configured);
    }

    public String getHost() {
        return host;
    }

    /** Label zur Kennzeichnung des Adresstyps: "AGW", "IS", "CLUSTER", "CLUSTER-CERT" oder null. */
    public String getLabel() {
        return label;
    }

    public boolean isReachable() {
        return reachable;
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Antwortzeit in ms, oder -1 wenn nicht erreichbar. */
    public long getResponseTimeMs() {
        return responseTimeMs;
    }
}
