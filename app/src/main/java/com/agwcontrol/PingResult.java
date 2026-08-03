package com.agwcontrol;

public class PingResult {

    private final String host;
    private final String label;
    private final boolean reachable;
    private final long responseTimeMs;

    public PingResult(String host, boolean reachable, long responseTimeMs) {
        this(host, null, reachable, responseTimeMs);
    }

    public PingResult(String host, String label, boolean reachable, long responseTimeMs) {
        this.host = host;
        this.label = label;
        this.reachable = reachable;
        this.responseTimeMs = responseTimeMs;
    }

    public PingResult withLabel(String label) {
        return new PingResult(this.host, label, this.reachable, this.responseTimeMs);
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

    /** Antwortzeit in ms, oder -1 wenn nicht erreichbar. */
    public long getResponseTimeMs() {
        return responseTimeMs;
    }
}
