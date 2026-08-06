package com.agwcontrol;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeePassConfigLoaderTest {

    private static final String MASTER_PASSWORD = "test1234";

    @BeforeAll
    static void ensureClusterUrlInTestKdbx() throws Exception {
        URL resource = KeePassConfigLoaderTest.class.getClassLoader().getResource("test.kdbx");
        assertNotNull(resource, "test.kdbx nicht in src/test/resources gefunden");
        UpdateTestKdbx.updateIfNeeded(Paths.get(resource.toURI()));
    }

    private Path testKdbx() throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("test.kdbx");
        assertNotNull(resource, "test.kdbx nicht in src/test/resources gefunden");
        return Paths.get(resource.toURI());
    }

    @Test
    void loadsCorrectNumberOfGroups() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        assertEquals(4, groups.size());
    }

    @Test
    void ohDevGroupHasThreeServers() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        ServerGroup ohDev = groups.stream()
                .filter(g -> "OH-DEV".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Gruppe OH-DEV nicht gefunden"));
        assertEquals(3, ohDev.getServers().size());
    }

    @Test
    void dn2020DevGroupHasOneServer() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        ServerGroup dn = groups.stream()
                .filter(g -> "DN2020-DEV".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Gruppe DN2020-DEV nicht gefunden"));
        assertEquals(1, dn.getServers().size());
    }

    @Test
    void groupNameIsPreserved() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        assertTrue(groups.stream().anyMatch(g -> "DN2020-DEV".equals(g.getName())));
        assertTrue(groups.stream().anyMatch(g -> "OH-DEV".equals(g.getName())));
        assertTrue(groups.stream().anyMatch(g -> "DN-PREPROD".equals(g.getName())));
        assertTrue(groups.stream().anyMatch(g -> "OH-PREPROD".equals(g.getName())));
    }

    @Test
    void parsesHostAndPort() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        ServerConfig first = groups.stream()
                .flatMap(g -> g.getServers().stream())
                .filter(c -> "vm40757.linux.oebb.at".equals(c.getHost()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eintrag vm40757.linux.oebb.at nicht gefunden"));
        assertEquals("vm40757.linux.oebb.at", first.getHost());
        assertEquals(443, first.getPort());
    }

    @Test
    void parsesUsernameAndPassword() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        ServerConfig entry = groups.stream()
                .flatMap(g -> g.getServers().stream())
                .filter(c -> "vm40757.linux.oebb.at".equals(c.getHost()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eintrag vm40757.linux.oebb.at nicht gefunden"));
        assertEquals("agwuser", entry.getUsername());
        assertEquals("agwpassword1", entry.getPassword());
    }

    @Test
    void throwsOnWrongPassword() throws URISyntaxException {
        Path kdbx = testKdbx();
        assertThrows(IOException.class,
                () -> new KeePassConfigLoader().loadGroups(kdbx, "falschesPasswort"));
    }

    @Test
    void parsesIsUrl() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        // vm40205.linux.gleis.at (OH-DEV) hat IS-URL gesetzt
        ServerConfig entry = groups.stream()
                .flatMap(g -> g.getServers().stream())
                .filter(c -> "vm40205.linux.gleis.at".equals(c.getHost()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eintrag vm40205.linux.gleis.at nicht gefunden"));
        assertEquals("https://vm40205.linux.gleis.at:443", entry.getIsUrl());
    }

    @Test
    void allEntriesHaveIsUrl() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        // DN2020-DEV hat keine IS-URL (Fallback-Test-Voraussetzung) – alle anderen schon
        groups.stream()
                .flatMap(g -> g.getServers().stream())
                .filter(c -> !"vm40757.linux.oebb.at".equals(c.getHost()))
                .forEach(c -> assertNotNull(c.getIsUrl(),
                        "IS-URL fehlt bei Eintrag: " + c.getHost()));
    }

    @Test
    void parsesClusterUrl() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        // OH-DEV erster Eintrag hat CLUSTER-URL (durch UpdateTestKdbx gesetzt)
        ServerGroup ohDev = groups.stream()
                .filter(g -> "OH-DEV".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Gruppe OH-DEV nicht gefunden"));
        ServerConfig entry = ohDev.getServers().get(0);
        assertNotNull(entry.getClusterUrl(), "CLUSTER-URL muss gesetzt sein");
        assertEquals("https://apigateway-oh-dev.oebb.at", entry.getClusterUrl());
    }

    @Test
    void parsesClusterCertUrl() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        ServerGroup ohDev = groups.stream()
                .filter(g -> "OH-DEV".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Gruppe OH-DEV nicht gefunden"));
        ServerConfig entry = ohDev.getServers().get(0);
        assertNotNull(entry.getClusterCertUrl(), "CLUSTER-CERT-URL muss gesetzt sein");
        assertEquals("https://apigateway-cert-oh-dev.oebb.at", entry.getClusterCertUrl());
    }

    @Test
    void missingClusterUrlReturnsNull() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        // DN2020-DEV hat keinen CLUSTER-URL → null erwartet
        ServerGroup dn = groups.stream()
                .filter(g -> "DN2020-DEV".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Gruppe DN2020-DEV nicht gefunden"));
        ServerConfig entry = dn.getServers().get(0);
        assertNull(entry.getClusterUrl(), "DN2020-DEV sollte keine CLUSTER-URL haben");
    }

    // --- IS-Probe config: IS-URL wird verwendet wenn vorhanden, sonst Fallback auf AGW-URL ---

    @Test
    void isProbeConfigUsesIsUrlWhenPresent() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        // vm40205.linux.gleis.at (OH-DEV) hat IS-URL = https://vm40205.linux.gleis.at:443
        // → isProbeConfig muss Scheme/Host/Port aus IS-URL verwenden
        ServerConfig entry = groups.stream()
                .flatMap(g -> g.getServers().stream())
                .filter(c -> "vm40205.linux.gleis.at".equals(c.getHost()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eintrag vm40205.linux.gleis.at nicht gefunden"));
        assertNotNull(entry.getIsUrl(), "IS-URL muss für diesen Eintrag gesetzt sein");
        IsEndpointCheckConfig probe = entry.getIsProbeConfig();
        assertNotNull(probe, "isProbeConfig muss für jeden Eintrag gesetzt sein");
        assertEquals("https",                  probe.getScheme());
        assertEquals("vm40205.linux.gleis.at", probe.getHost());
        assertEquals(443,                      probe.getPort());
        assertEquals("agwuser",                probe.getUsername());
        assertEquals("agwpassword2",           probe.getPassword());
    }

    @Test
    void isProbeConfigBuiltFromStandardFields() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        // vm40757.linux.oebb.at (DN2020-DEV) hat keine IS-URL
        // → isProbeConfig wird als Fallback aus der AGW-URL gebaut
        ServerConfig entry = groups.stream()
                .flatMap(g -> g.getServers().stream())
                .filter(c -> "vm40757.linux.oebb.at".equals(c.getHost()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eintrag vm40757.linux.oebb.at nicht gefunden"));
        assertNull(entry.getIsUrl(), "DN2020-DEV darf keine IS-URL haben (Fallback-Test)");
        IsEndpointCheckConfig probe = entry.getIsProbeConfig();
        assertNotNull(probe, "isProbeConfig muss für jeden Eintrag gesetzt sein");
        assertEquals("https",                   probe.getScheme());
        assertEquals("vm40757.linux.oebb.at",   probe.getHost());
        assertEquals(443,                        probe.getPort());
        assertEquals("agwuser",                 probe.getUsername());
        assertEquals("agwpassword1",            probe.getPassword());
    }

    @Test
    void allEntriesHaveIsProbeConfig() throws Exception {
        List<ServerGroup> groups = new KeePassConfigLoader().loadGroups(testKdbx(), MASTER_PASSWORD);
        groups.stream()
                .flatMap(g -> g.getServers().stream())
                .forEach(c -> assertNotNull(c.getIsProbeConfig(),
                        "isProbeConfig fehlt bei Eintrag: " + c.getHost()));
    }
}
