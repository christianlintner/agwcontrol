package com.agwcontrol;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.*;
import de.slackspace.openkeepass.domain.zipper.GroupZipper;

import java.nio.file.Path;
import java.util.List;

/**
 * Aktualisiert die test.kdbx einmalig:
 * <ul>
 *   <li>Setzt CLUSTER-URL und CLUSTER-CERT-URL im ersten Eintrag der OH-DEV-Gruppe.</li>
 *   <li>Entfernt IS-URL aus dem Eintrag der DN2020-DEV-Gruppe (Fallback-Test-Voraussetzung).</li>
 * </ul>
 */
class UpdateTestKdbx {

    static final String MASTER_PASSWORD = "test1234";

    /**
     * Aktualisiert die test.kdbx und gibt zurück ob eine Änderung vorgenommen wurde.
     */
    static boolean updateIfNeeded(Path kdbxPath) throws Exception {
        KeePassFile db = KeePassDatabase.getInstance(kdbxPath.toFile()).openDatabase(MASTER_PASSWORD);

        boolean needsUpdate = false;

        // --- OH-DEV: CLUSTER-URL / CLUSTER-CERT-URL setzen, falls noch nicht vorhanden ---
        Group ohDev = findGroup(db.getRoot(), "OH-DEV");
        if (ohDev == null) throw new IllegalStateException("OH-DEV nicht gefunden");

        boolean clusterUrlMissing = ohDev.getEntries().stream()
                .allMatch(e -> {
                    Property p = e.getPropertyByName("CLUSTER-URL");
                    return p == null || p.getValue() == null || p.getValue().isEmpty();
                });

        if (clusterUrlMissing) {
            Entry first = ohDev.getEntries().get(0);
            EntryBuilder eb = new EntryBuilder(first);
            eb.getCustomPropertyList().add(new Property("CLUSTER-URL",      "https://apigateway-oh-dev.oebb.at",       false));
            eb.getCustomPropertyList().add(new Property("CLUSTER-CERT-URL", "https://apigateway-cert-oh-dev.oebb.at", false));
            Entry updated = eb.build();

            GroupBuilder newGb = new GroupBuilder(ohDev.getName());
            for (Entry e : ohDev.getEntries()) {
                newGb.addEntry(e.getUuid().equals(first.getUuid()) ? updated : e);
            }
            for (Group sub : ohDev.getGroups()) newGb.addGroup(sub);
            Group newOhDev = newGb.build();

            GroupZipper zipper = new GroupZipper(db).down(); // AGW-Server
            zipper = zipper.down();
            while (!zipper.getNode().getName().equals("OH-DEV")) zipper = zipper.right();
            db = zipper.replace(newOhDev).close();
            needsUpdate = true;
        }

        // --- DN2020-DEV: IS-URL entfernen, damit der Fallback-Pfad testbar ist ---
        Group dn2020Dev = findGroup(db.getRoot(), "DN2020-DEV");
        if (dn2020Dev == null) throw new IllegalStateException("DN2020-DEV nicht gefunden");

        boolean isUrlStillPresent = dn2020Dev.getEntries().stream()
                .anyMatch(e -> {
                    Property p = e.getPropertyByName("IS-URL");
                    return p != null && p.getValue() != null && !p.getValue().isEmpty();
                });

        if (isUrlStillPresent) {
            GroupBuilder newGb = new GroupBuilder(dn2020Dev.getName());
            for (Entry e : dn2020Dev.getEntries()) {
                EntryBuilder eb = new EntryBuilder(e);
                List<Property> props = eb.getCustomPropertyList();
                props.removeIf(p -> "IS-URL".equals(p.getKey()));
                newGb.addEntry(eb.build());
            }
            for (Group sub : dn2020Dev.getGroups()) newGb.addGroup(sub);
            Group newDn = newGb.build();

            GroupZipper zipper = new GroupZipper(db).down(); // AGW-Server
            zipper = zipper.down();
            while (!zipper.getNode().getName().equals("DN2020-DEV")) zipper = zipper.right();
            db = zipper.replace(newDn).close();
            needsUpdate = true;
        }

        if (needsUpdate) {
            KeePassDatabase.write(db, MASTER_PASSWORD, kdbxPath.toString());
        }
        return needsUpdate;
    }

    private static Group findGroup(Group group, String name) {
        if (name.equals(group.getName())) return group;
        for (Group sub : group.getGroups()) {
            Group found = findGroup(sub, name);
            if (found != null) return found;
        }
        return null;
    }
}
