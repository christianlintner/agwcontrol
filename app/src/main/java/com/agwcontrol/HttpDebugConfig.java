package com.agwcontrol;

public class HttpDebugConfig {

    private boolean enabled;
    private boolean includeResponseBody = true;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldIncludeResponseBody() {
        return enabled && includeResponseBody;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public String label() {
        return enabled ? "AN" : "AUS";
    }

    public String labelAfterToggle() {
        return enabled ? "AUS" : "AN";
    }
}
