package com.agwcontrol;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class PingService {

    public static final int DEFAULT_TIMEOUT_MS = 2000;

    public PingResult ping(ServerConfig server) {
        return ping(server.getHost(), DEFAULT_TIMEOUT_MS);
    }

    public PingResult ping(ServerConfig server, int timeoutMs) {
        return ping(server.getHost(), timeoutMs);
    }

    public PingResult ping(String host, int timeoutMs) {
        long start = System.currentTimeMillis();
        try {
            boolean reachable = java.net.InetAddress.getByName(host).isReachable(timeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            return new PingResult(host, reachable, reachable ? elapsed : -1);
        } catch (Exception e) {
            return new PingResult(host, false, -1);
        }
    }

    /**
     * Pingt alle konfigurierten Adressen eines Server-Eintrags:
     * AGW-Node-Host, IS-Host, CLUSTER-Host und CLUSTER-CERT-Host.
     * Fehlende URLs (null) werden übersprungen.
     */
    public List<PingResult> pingAll(ServerConfig server) {
        List<PingResult> results = new ArrayList<>();
        results.add(ping(server.getHost(), DEFAULT_TIMEOUT_MS).withLabel("AGW"));
        if (server.getIsUrl() != null) {
            String isHost = hostFromUrl(server.getIsUrl());
            if (isHost != null) {
                results.add(ping(isHost, DEFAULT_TIMEOUT_MS).withLabel("IS"));
            }
        }
        if (server.getClusterUrl() != null) {
            String clusterHost = hostFromUrl(server.getClusterUrl());
            if (clusterHost != null) {
                results.add(ping(clusterHost, DEFAULT_TIMEOUT_MS).withLabel("CLUSTER"));
            }
        }
        if (server.getClusterCertUrl() != null) {
            String clusterCertHost = hostFromUrl(server.getClusterCertUrl());
            if (clusterCertHost != null) {
                results.add(ping(clusterCertHost, DEFAULT_TIMEOUT_MS).withLabel("CLUSTER-CERT"));
            }
        }
        return results;
    }

    static String hostFromUrl(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
