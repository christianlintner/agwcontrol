package com.agwcontrol;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Entry;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class KeePassConfigLoader {

    public List<ServerConfig> load(Path kdbxFile, String masterPassword) throws IOException {
        KeePassFile db;
        try {
            db = KeePassDatabase.getInstance(kdbxFile.toFile()).openDatabase(masterPassword);
        } catch (KeePassDatabaseUnreadableException e) {
            throw new IOException("KeePass-Datenbank konnte nicht geöffnet werden: " + e.getMessage(), e);
        }

        List<ServerConfig> result = new ArrayList<>();
        for (Entry entry : db.getEntries()) {
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
            result.add(new ServerConfig(host, port, entry.getUsername(), entry.getPassword()));
        }
        return result;
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
