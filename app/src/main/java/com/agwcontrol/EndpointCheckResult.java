package com.agwcontrol;

public class EndpointCheckResult {

    private final String apiName;
    private final String apiVersion;
    private final String url;
    private final int httpStatus;
    private final boolean reachable;
    private final String errorMsg;

    public EndpointCheckResult(String apiName, String apiVersion,
                               String url, int httpStatus,
                               boolean reachable, String errorMsg) {
        this.apiName = apiName;
        this.apiVersion = apiVersion;
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
