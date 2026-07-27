package com.agwcontrol;

/**
 * Steuert pro Funktion, ob Daten aus der lokalen DB (Cache) oder
 * frisch vom Server geladen werden sollen.
 *
 * <p>Standard: {@code useDb = false} – d.h. beim ersten Start wird immer
 * vom Server geladen. Der Nutzer kann per {@link #toggleAll()} umschalten.
 */
public class DbCacheConfig {

    private boolean useDbForApis;
    private boolean useDbForEndpoints;

    /** Standard: Server (neu laden). */
    public DbCacheConfig() {
        this.useDbForApis      = false;
        this.useDbForEndpoints = false;
    }

    public boolean isUseDbForApis() {
        return useDbForApis;
    }

    public boolean isUseDbForEndpoints() {
        return useDbForEndpoints;
    }

    /**
     * Schaltet beide Flags gleichzeitig um (Session-Toggle).
     * DB → Server oder Server → DB.
     */
    public void toggleAll() {
        useDbForApis      = !useDbForApis;
        useDbForEndpoints = !useDbForEndpoints;
    }

    /** Bezeichnung des aktuellen Modus für die Menüanzeige. */
    public String label() {
        return useDbForApis ? "DB" : "Server (neu laden)";
    }

    /** Bezeichnung des Ziel-Modus nach einem Toggle (für den Hinweis im Menü). */
    public String labelAfterToggle() {
        return useDbForApis ? "Server (neu laden)" : "DB";
    }
}
