package com.agwcontrol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

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
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            return new TcpCheckResult(host, port, true, elapsed);
        } catch (IOException e) {
            return new TcpCheckResult(host, port, false, -1);
        }
    }
}
