package com.agwcontrol;

import java.net.InetAddress;

public class PingService {

    public static final int DEFAULT_TIMEOUT_MS = 2000;

    public PingResult ping(ServerConfig server) {
        return ping(server, DEFAULT_TIMEOUT_MS);
    }

    public PingResult ping(ServerConfig server, int timeoutMs) {
        String host = server.getHost();
        long start = System.currentTimeMillis();
        try {
            boolean reachable = InetAddress.getByName(host).isReachable(timeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            return new PingResult(host, reachable, reachable ? elapsed : -1);
        } catch (Exception e) {
            return new PingResult(host, false, -1);
        }
    }
}
