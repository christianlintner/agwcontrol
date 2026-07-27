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

    /** Konstruktor ohne Alias (Rückwärtskompatibilität für Tests). */
    public EndpointCheckResult(String apiName, String apiVersion,
                               String url, int httpStatus,
                               boolean reachable, String errorMsg) {
        this(apiName, apiVersion, null, url, httpStatus, reachable, errorMsg);
    }

    public EndpointCheckResult(String apiName, String apiVersion,
                               String aliasName,
                               String url, int httpStatus,
                               boolean reachable, String errorMsg) {
        this.apiName = apiName;
        this.apiVersion = apiVersion;
        this.aliasName = aliasName;
        this.url = url;
        this.httpStatus = httpStatus;
        this.reachable = reachable;
        this.errorMsg = errorMsg;
    }

    public String getApiName() {
        return apiName;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    /** @return Alias-Name oder null wenn direkter Endpoint. */
    public String getAliasName() {
        return aliasName;
    }

    public String getUrl() {
        return url;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isReachable() {
        return reachable;
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}
