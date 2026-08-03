package com.agwcontrol;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class TcpCheckService {

    public static final int DEFAULT_TIMEOUT_MS = 2000;

    public TcpCheckResult check(ServerConfig server) {
        return check(server.getHost(), server.getPort(), DEFAULT_TIMEOUT_MS);
    }

    public TcpCheckResult check(ServerConfig server, int timeoutMs) {
        return check(server.getHost(), server.getPort(), timeoutMs);
    }

    public TcpCheckResult check(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            return new TcpCheckResult(host, port, true, elapsed);
        } catch (java.io.IOException e) {
            return new TcpCheckResult(host, port, false, -1);
        }
    }

    /**
     * Prüft alle konfigurierten Adressen eines Server-Eintrags per TCP:
     * AGW-Node-Host, IS-Host, CLUSTER-Host und CLUSTER-CERT-Host.
     * Fehlende URLs (null) werden übersprungen.
     */
    public List<TcpCheckResult> checkAll(ServerConfig server) {
        List<TcpCheckResult> results = new ArrayList<>();
        results.add(check(server.getHost(), server.getPort(), DEFAULT_TIMEOUT_MS).withLabel("AGW"));
        if (server.getIsUrl() != null) {
            String isHost = hostFromUrl(server.getIsUrl());
            int isPort = portFromUrl(server.getIsUrl());
            if (isHost != null) {
                results.add(check(isHost, isPort, DEFAULT_TIMEOUT_MS).withLabel("IS"));
            }
        }
        if (server.getClusterUrl() != null) {
            String clusterHost = hostFromUrl(server.getClusterUrl());
            int clusterPort = portFromUrl(server.getClusterUrl());
            if (clusterHost != null) {
                results.add(check(clusterHost, clusterPort, DEFAULT_TIMEOUT_MS).withLabel("CLUSTER"));
            }
        }
        if (server.getClusterCertUrl() != null) {
            String clusterCertHost = hostFromUrl(server.getClusterCertUrl());
            int clusterCertPort = portFromUrl(server.getClusterCertUrl());
            if (clusterCertHost != null) {
                results.add(check(clusterCertHost, clusterCertPort, DEFAULT_TIMEOUT_MS).withLabel("CLUSTER-CERT"));
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

    static int portFromUrl(String url) {
        try {
            int port = new URI(url).getPort();
            return port == -1 ? 443 : port;
        } catch (URISyntaxException e) {
            return 443;
        }
    }
}
