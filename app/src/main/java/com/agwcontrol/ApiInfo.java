package com.agwcontrol;

public class ApiInfo {

    private final String id;
    private final String name;
    private final String version;
    private final String type;
    private final boolean active;

    public ApiInfo(String id, String name, String version, String type, boolean active) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.type = type;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }
}
