package com.agwcontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IsEndpointCheckConfigTest {

    // --- Constructor validation ---

    @Test
    void constructorStoresAllFields() {
        IsEndpointCheckConfig cfg = new IsEndpointCheckConfig("https", "is.example.com", 5443, "admin", "secret");
        assertEquals("https",          cfg.getScheme());
        assertEquals("is.example.com", cfg.getHost());
        assertEquals(5443,             cfg.getPort());
        assertEquals("admin",          cfg.getUsername());
        assertEquals("secret",         cfg.getPassword());
    }

    @Test
    void convenienceConstructorDefaultsToHttpAnd5555() {
        IsEndpointCheckConfig cfg = new IsEndpointCheckConfig("myhost", "user", "pass");
        assertEquals("http",   cfg.getScheme());
        assertEquals("myhost", cfg.getHost());
        assertEquals(5555,     cfg.getPort());
    }

    @Test
    void nullSchemeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckConfig(null, "host", 5555, "u", "p"));
    }

    @Test
    void emptySchemeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckConfig("", "host", 5555, "u", "p"));
    }

    @Test
    void nullHostThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckConfig("http", null, 5555, "u", "p"));
    }

    @Test
    void emptyHostThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckConfig("http", "", 5555, "u", "p"));
    }

    @Test
    void zeroPortThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckConfig("http", "host", 0, "u", "p"));
    }

    @Test
    void negativePortThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckConfig("http", "host", -1, "u", "p"));
    }

    @Test
    void portAbove65535Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsEndpointCheckConfig("http", "host", 65536, "u", "p"));
    }

    // --- buildBaseUrl() ---

    @Test
    void buildBaseUrlHttp() {
        IsEndpointCheckConfig cfg = new IsEndpointCheckConfig("http", "localhost", 5555, "u", "p");
        assertEquals(
                "http://localhost:5555/rad/at.oebb.infra.pro.agwctl.pub.rs.v1:checkRAD",
                cfg.buildBaseUrl());
    }

    @Test
    void buildBaseUrlHttps() {
        IsEndpointCheckConfig cfg = new IsEndpointCheckConfig("https", "is.corp.at", 5443, "u", "p");
        assertEquals(
                "https://is.corp.at:5443/rad/at.oebb.infra.pro.agwctl.pub.rs.v1:checkRAD",
                cfg.buildBaseUrl());
    }

    // --- toString() ---

    @Test
    void toStringContainsHostAndPort() {
        IsEndpointCheckConfig cfg = new IsEndpointCheckConfig("http", "is.example.com", 5555, "u", "p");
        String s = cfg.toString();
        assertTrue(s.contains("is.example.com"));
        assertTrue(s.contains("5555"));
    }

    @Test
    void toStringDoesNotContainPassword() {
        IsEndpointCheckConfig cfg = new IsEndpointCheckConfig("http", "host", 5555, "admin", "supersecret");
        assertFalse(cfg.toString().contains("supersecret"),
                "toString() must not expose the password");
    }

    // --- Constants ---

    @Test
    void defaultPortsAreCorrect() {
        assertEquals(5555, IsEndpointCheckConfig.DEFAULT_PORT_HTTP);
        assertEquals(5443, IsEndpointCheckConfig.DEFAULT_PORT_HTTPS);
    }

    @Test
    void radBasePathIsCorrect() {
        assertEquals(
                "/rad/at.oebb.infra.pro.agwctl.pub.rs.v1:checkRAD",
                IsEndpointCheckConfig.RAD_BASE_PATH);
    }
}
