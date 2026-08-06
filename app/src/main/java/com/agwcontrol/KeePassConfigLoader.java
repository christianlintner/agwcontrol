package com.agwcontrol;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Entry;
import de.slackspace.openkeepass.domain.Group;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.domain.Property;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class KeePassConfigLoader {

    /**
     * Lädt alle AGW-Server-Gruppen aus der KeePass-Datei.
     * Jede Sub-Gruppe unter "AGW-Server" wird zu einer {@link ServerGroup}.
     */
    public List<ServerGroup> loadGroups(Path kdbxFile, String masterPassword) throws IOException {
        KeePassFile db = openDatabase(kdbxFile, masterPassword);

        Group agwServerGroup = findAgwServerGroup(db.getRoot());
        if (agwServerGroup == null) {
            throw new IOException("Gruppe \"AGW-Server\" wurde in der KeePass-Datei nicht gefunden.");
        }

        List<ServerGroup> result = new ArrayList<>();
        for (Group sub : agwServerGroup.getGroups()) {
            List<ServerConfig> servers = entriesFromGroup(sub);
            result.add(new ServerGroup(sub.getName(), servers));
        }
        return result;
    }

    private KeePassFile openDatabase(Path kdbxFile, String masterPassword) throws IOException {
        try {
            return KeePassDatabase.getInstance(kdbxFile.toFile()).openDatabase(masterPassword);
        } catch (KeePassDatabaseUnreadableException e) {
            throw new IOException("KeePass-Datenbank konnte nicht geöffnet werden: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new IOException("KeePass-Datei nicht gefunden: " + kdbxFile, e);
        }
    }

    /** Sucht rekursiv nach der Gruppe "AGW-Server". */
    private Group findAgwServerGroup(Group group) {
        if ("AGW-Server".equals(group.getName())) {
            return group;
        }
        for (Group sub : group.getGroups()) {
            Group found = findAgwServerGroup(sub);
            if (found != null) return found;
        }
        return null;
    }

    private List<ServerConfig> entriesFromGroup(Group group) {
        List<ServerConfig> servers = new ArrayList<>();
        for (Entry entry : group.getEntries()) {
            String url = entry.getUrl();
            if (url == null || url.trim().isEmpty()) {
                System.err.println("Warnung: Eintrag \"" + entry.getTitle() + "\" hat keine URL – wird übersprungen.");
                continue;
            }
            String host = parseHost(url);
            int port = parsePort(url);
            if (host == null) {
                System.err.println("Warnung: URL \"" + url + "\" in Eintrag \"" + entry.getTitle() + "\" konnte nicht geparst werden – wird übersprungen.");
                continue;
            }
            String isUrl          = readCustomField(entry, "IS-URL");
            String clusterUrl     = readCustomField(entry, "CLUSTER-URL");
            String clusterCertUrl = readCustomField(entry, "CLUSTER-CERT-URL");
            ServerConfig config = new ServerConfig(host, port, entry.getUsername(), entry.getPassword(),
                    isUrl, clusterUrl, clusterCertUrl);

            // Optional IS probe configuration — read from custom fields:
            //   IS-PROBE-URL      e.g. http://localhost:5555  (scheme + host + port)
            //   IS-PROBE-USER     IS username (Administrators role required)
            //   IS-PROBE-PASSWORD IS password
            IsEndpointCheckConfig probeConfig = buildProbeConfig(entry);
            if (probeConfig != null) {
                config.setIsProbeConfig(probeConfig);
            }
            servers.add(config);
        }
        return servers;
    }

    /**
     * Reads IS-PROBE-URL, IS-PROBE-USER and IS-PROBE-PASSWORD custom fields
     * from a KeePass entry and builds an {@link IsEndpointCheckConfig} if all
     * three are present.
     *
     * <p>IS-PROBE-URL must include the scheme and port, e.g.
     * {@code http://is-server:5555} or {@code https://is-server:5443}.</p>
     *
     * @return populated config, or {@code null} if any required field is absent
     */
    private IsEndpointCheckConfig buildProbeConfig(Entry entry) {
        String probeUrl  = readCustomField(entry, "IS-PROBE-URL");
        String probeUser = readCustomField(entry, "IS-PROBE-USER");
        String probePass = readCustomField(entry, "IS-PROBE-PASSWORD");

        if (probeUrl == null || probeUser == null || probePass == null) {
            return null;
        }

        String scheme = parseScheme(probeUrl);
        String probeHost = parseHost(probeUrl);
        int    probePort = parsePort(probeUrl);

        if (probeHost == null || probeHost.isEmpty()) {
            System.err.println("Warnung: IS-PROBE-URL \"" + probeUrl + "\" konnte nicht geparst werden – IS-Probe deaktiviert.");
            return null;
        }

        return new IsEndpointCheckConfig(scheme, probeHost, probePort, probeUser, probePass);
    }

    private String parseScheme(String url) {
        try {
            String scheme = new URI(url).getScheme();
            return (scheme != null && !scheme.isEmpty()) ? scheme : "http";
        } catch (URISyntaxException e) {
            return "http";
        }
    }

    /**
     * Liest ein Custom Field aus einem KeePass-Eintrag.
     * Fehlt das Feld oder ist es leer, wird null zurückgegeben.
     */
    private String readCustomField(Entry entry, String fieldName) {
        Property prop = entry.getPropertyByName(fieldName);
        if (prop == null) return null;
        String value = prop.getValue();
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    private String parseHost(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private int parsePort(String url) {
        try {
            int port = new URI(url).getPort();
            return port == -1 ? 443 : port;
        } catch (URISyntaxException e) {
            return 443;
        }
    }
}
