package com.agwcontrol;

import java.util.Collections;
import java.util.List;

public class ServerGroup {

    private final String name;
    private final List<ServerConfig> servers;

    public ServerGroup(String name, List<ServerConfig> servers) {
        this.name = name;
        this.servers = Collections.unmodifiableList(servers);
    }

    public String getName() {
        return name;
    }

    public List<ServerConfig> getServers() {
        return servers;
    }
}
