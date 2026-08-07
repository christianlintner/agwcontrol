package com.agwcontrol;

/**
 * Kapselt den aufgelösten Routing-Endpoint einer API.
 *
 * <p>Wenn {@code isAlias} {@code true} ist, wurde die URL über
 * {@code GET /rest/apigateway/alias/{aliasName}} aufgelöst.
 * In diesem Fall enthält {@code aliasName} den Alias-Namen (z. B. "MystageEndpoint")
 * und {@code resolvedUrl} die {@code endPointURI} des Alias.
 *
 * <p>Wenn {@code isAlias} {@code false} ist, ist {@code resolvedUrl} die direkte
 * Backend-URL aus {@code nativeEndpoint[].uri} und {@code aliasName} ist {@code null}.
 */
public class RoutingEndpoint {

    private final String aliasName;    // null wenn kein Alias
    private final String resolvedUrl;  // aufgelöste URL (endPointURI oder direkte uri)
    private final boolean isAlias;
    private String resolvedIp;         // via IS-Service resolveHost aufgelöste IP, null wenn unbekannt

    public RoutingEndpoint(String aliasName, String resolvedUrl, boolean isAlias) {
        this.aliasName = aliasName;
        this.resolvedUrl = resolvedUrl;
        this.isAlias = isAlias;
    }

    /** Erstellt einen direkten (nicht-Alias) Routing-Endpoint. */
    public static RoutingEndpoint direct(String url) {
        return new RoutingEndpoint(null, url, false);
    }

    /** Erstellt einen Alias-basierten Routing-Endpoint. */
    public static RoutingEndpoint alias(String aliasName, String resolvedUrl) {
        return new RoutingEndpoint(aliasName, resolvedUrl, true);
    }

    /** @return Alias-Name oder {@code null} wenn kein Alias. */
    public String getAliasName() {
        return aliasName;
    }

    /**
     * @return Die aufgelöste URL, oder {@code null} wenn der Alias im Zielsystem nicht
     *         aufgelöst werden konnte (z.&nbsp;B. kein {@code endPointURI} im Response).
     */
    public String getResolvedUrl() {
        return resolvedUrl;
    }

    /** @return {@code true} wenn dieser Endpoint über einen Alias aufgelöst wurde. */
    public boolean isAlias() {
        return isAlias;
    }

    /**
     * Gibt den anzuzeigenden Bezeichner zurück:
     * bei Alias den Alias-Namen, sonst die direkte URL.
     */
    public String getDisplayLabel() {
        return isAlias ? aliasName : resolvedUrl;
    }

    /**
     * @return Die via IS-Service {@code resolveHost} aufgelöste IP-Adresse,
     *         oder {@code null} wenn noch nicht aufgelöst oder Auflösung fehlgeschlagen.
     */
    public String getResolvedIp() {
        return resolvedIp;
    }

    /** Setzt die via IS-Service aufgelöste IP-Adresse. */
    public void setResolvedIp(String resolvedIp) {
        this.resolvedIp = resolvedIp;
    }
}
