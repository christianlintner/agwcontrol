package com.agwcontrol;

public class PingResult {

    private final String host;
    private final boolean reachable;
    private final long responseTimeMs;

    public PingResult(String host, boolean reachable, long responseTimeMs) {
        this.host = host;
        this.reachable = reachable;
        this.responseTimeMs = responseTimeMs;
    }

    public String getHost() {
        return host;
    }

    public boolean isReachable() {
        return reachable;
    }

    /** Antwortzeit in ms, oder -1 wenn nicht erreichbar. */
    public long getResponseTimeMs() {
        return responseTimeMs;
    }
}
