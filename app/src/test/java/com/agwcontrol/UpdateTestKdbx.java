package com.agwcontrol;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.*;
import de.slackspace.openkeepass.domain.zipper.GroupZipper;

import java.nio.file.Path;

/**
 * Aktualisiert die test.kdbx: Setzt CLUSTER-URL und CLUSTER-CERT-URL
 * Custom Fields im ersten Eintrag der OH-DEV Gruppe.
 *
 * Wird einmalig ausgeführt, falls die Felder noch nicht vorhanden sind.
 */
class UpdateTestKdbx {

    static final String MASTER_PASSWORD = "test1234";

    /**
     * Aktualisiert die test.kdbx und gibt zurück ob eine Änderung vorgenommen wurde.
     */
    static boolean updateIfNeeded(Path kdbxPath) throws Exception {
        KeePassFile db = KeePassDatabase.getInstance(kdbxPath.toFile()).openDatabase(MASTER_PASSWORD);

        // Prüfen ob bereits vorhanden
        Group ohDev = findGroup(db.getRoot(), "OH-DEV");
        if (ohDev == null) throw new IllegalStateException("OH-DEV nicht gefunden");

        for (Entry e : ohDev.getEntries()) {
            Property p = e.getPropertyByName("CLUSTER-URL");
            if (p != null && p.getValue() != null && !p.getValue().isEmpty()) {
                return false; // bereits vorhanden
            }
        }

        // Ersten Eintrag mit CLUSTER-URL + CLUSTER-CERT-URL ergänzen
        Entry first = ohDev.getEntries().get(0);
        EntryBuilder eb = new EntryBuilder(first);
        eb.getCustomPropertyList().add(new Property("CLUSTER-URL",      "https://apigateway-oh-dev.oebb.at",       false));
        eb.getCustomPropertyList().add(new Property("CLUSTER-CERT-URL", "https://apigateway-cert-oh-dev.oebb.at", false));
        Entry updated = eb.build();

        // Gruppe neu bauen mit dem aktualisierten Eintrag
        GroupBuilder newGb = new GroupBuilder(ohDev.getName());
        for (Entry e : ohDev.getEntries()) {
            if (e.getUuid().equals(first.getUuid())) {
                newGb.addEntry(updated);
            } else {
                newGb.addEntry(e);
            }
        }
        for (Group sub : ohDev.getGroups()) {
            newGb.addGroup(sub);
        }
        Group newOhDev = newGb.build();

        // Per GroupZipper navigieren: Root → AGW-Server (down) → OH-DEV (down + ggf. right)
        GroupZipper zipper = new GroupZipper(db).down(); // AGW-Server
        zipper = zipper.down(); // erste Untergruppe
        while (!zipper.getNode().getName().equals("OH-DEV")) {
            zipper = zipper.right();
        }
        KeePassFile updatedDb = zipper.replace(newOhDev).close();
        KeePassDatabase.write(updatedDb, MASTER_PASSWORD, kdbxPath.toString());
        return true;
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
