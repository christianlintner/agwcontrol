package com.agwcontrol;

public class EndpointCheckResult {

    private final String apiName;
    private final String apiVersion;
    /** Alias-Name wenn Routing über Endpoint-Alias, sonst null. */
    private final String aliasName;
    private final String url;
    private final int httpStatus;
    private final boolean reachable;
    private final String errorMsg;
    private final boolean pingOk;
    private final long pingMs;   // -1 wenn nicht verfügbar
    private final boolean tcpOk;
    private final long tcpMs;    // -1 wenn nicht verfügbar

    /** Konstruktor ohne Alias und ohne Ping/TCP (Rückwärtskompatibilität für Tests). */
    public EndpointCheckResult(String apiName, String apiVersion,
                               String url, int httpStatus,
                               boolean reachable, String errorMsg) {
        this(apiName, apiVersion, null, url, httpStatus, reachable, errorMsg, false, -1, false, -1);
    }

    /** Konstruktor ohne Ping/TCP (Rückwärtskompatibilität). */
    public EndpointCheckResult(String apiName, String apiVersion,
                               String aliasName,
                               String url, int httpStatus,
                               boolean reachable, String errorMsg) {
        this(apiName, apiVersion, aliasName, url, httpStatus, reachable, errorMsg, false, -1, false, -1);
    }

    /** Vollständiger Konstruktor mit Ping + TCP. */
    public EndpointCheckResult(String apiName, String apiVersion,
                               String aliasName,
                               String url, int httpStatus,
                               boolean reachable, String errorMsg,
                               boolean pingOk, long pingMs,
                               boolean tcpOk, long tcpMs) {
        this.apiName = apiName;
        this.apiVersion = apiVersion;
        this.aliasName = aliasName;
        this.url = url;
        this.httpStatus = httpStatus;
        this.reachable = reachable;
        this.errorMsg = errorMsg;
        this.pingOk = pingOk;
        this.pingMs = pingMs;
        this.tcpOk = tcpOk;
        this.tcpMs = tcpMs;
    }

    public String getApiName()    { return apiName; }
    public String getApiVersion() { return apiVersion; }

    /** @return Alias-Name oder null wenn direkter Endpoint. */
    public String getAliasName()  { return aliasName; }

    public String getUrl()        { return url; }
    public int getHttpStatus()    { return httpStatus; }
    public boolean isReachable()  { return reachable; }
    public String getErrorMsg()   { return errorMsg; }
    public boolean isPingOk()     { return pingOk; }
    /** @return Ping-Antwortzeit in ms, oder -1 wenn nicht verfügbar. */
    public long getPingMs()       { return pingMs; }
    public boolean isTcpOk()      { return tcpOk; }
    /** @return TCP-Verbindungszeit in ms, oder -1 wenn nicht verfügbar. */
    public long getTcpMs()        { return tcpMs; }
}
