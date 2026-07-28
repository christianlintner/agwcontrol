# Issue #15 – Endpoint-Auflösung via Routing Policy Alias

Branch: `feature/issue-15-resolve-endpoint-routing-policy`

## Status: ✅ Implementiert + ✅ Alias-Cache optimiert

---

## Problem

Der tatsächliche Backend-Endpoint einer API ist **immer** in der `straightThroughRouting`-Policy
hinterlegt — nicht in `nativeEndpoint`. Das Feld `nativeEndpoint` ist fachlich irrelevant und
darf nicht mehr als primäre Quelle verwendet werden.

---

## Tatsächlicher API-Flow (ermittelt durch Live-Tests gegen echten Server)

```
GET /apis/{apiId}
  └─ api.policies[]                              →  Policy-IDs (keine Policy Action IDs!)
       └─ GET /policies/{policyId}               →  policy.policyEnforcements[].enforcements[].enforcementObjectId
            └─ GET /policyActions?policyActionIds=act-1,act-2,...
                 └─ policyAction[templateKey=="straightThroughRouting"]
                      └─ parameters[templateKey=="endpointUri"].values[0]
                           └─ "${AliasName}/..."  →  Regex \$\{([^}]+)\}  →  AliasName
                                └─ GET /alias    →  Alias-Liste
                                     └─ Eintrag mit "name"=="AliasName"  →  endPointURI
```

> **Wichtig:** `api.policies[]` enthält **Policy-IDs**, nicht Policy Action IDs.
> Pro Policy muss zuerst `GET /policies/{policyId}` aufgerufen werden um die
> `enforcementObjectId`-Werte (= Policy Action IDs) zu erhalten.
> Erst danach können die Policy Actions per Bulk-Call abgerufen werden.

> **Wichtig:** `GET /alias/{name}` liefert kein zuverlässiges Ergebnis wenn der Name
> als Pfad-Parameter verwendet wird (Server interpretiert ihn als UUID).
> Stattdessen wird `GET /alias` (alle Aliases) aufgerufen und nach `"name"` gefiltert.

---

## API-Strukturen (verifiziert gegen echten Server)

### GET /rest/apigateway/apis/{apiId}
```json
{
  "apiResponse": {
    "api": {
      "nativeEndpoint": [{ "uri": "http://akos.oebb.at/...", "alias": false }],
      "policies": ["e69595be-98d9-4fde-b511-aa661ebc2a21"]
    }
  }
}
```

### GET /rest/apigateway/policies/{policyId}
```json
{
  "policy": {
    "policyEnforcements": [
      {
        "enforcements": [{ "enforcementObjectId": "action-uuid-1" }],
        "stageKey": "routing"
      }
    ]
  }
}
```

### GET /rest/apigateway/policyActions?policyActionIds=action-uuid-1,...
```json
{
  "policyAction": [
    {
      "id": "action-uuid-1",
      "templateKey": "straightThroughRouting",
      "parameters": [
        {
          "templateKey": "endpointUri",
          "values": [ "${AKOS_API_EndpointAlias}/${sys:resource_path}" ]
        }
      ]
    }
  ]
}
```

### GET /rest/apigateway/alias  (gefiltert nach name)
```json
[
  { "name": "AKOS_API_EndpointAlias", "endPointURI": "https://real-backend:8080", "type": "endpoint" },
  { "name": "OtherAlias", "type": "simple" }
]
```

---

## Wie wird die richtige Policy Action identifiziert?

Per `templateKey == "straightThroughRouting"` — laut `spec/APIGatewayPolicyManagement.json`
der einzige Routing-spezifische Key. Eine API hat typischerweise mehrere Policy Actions
(Transport Security, OAuth, Rate Limiting, Routing) — der Bulk-Call liefert alle auf einmal,
der Code filtert auf `straightThroughRouting`.

---

## Implementierung (fertig)

### Neue / geänderte Methoden in `AgwApiService.java`

| Methode | Art | Beschreibung |
|---|---|---|
| `getNativeEndpoints(ServerConfig, String)` | geändert | Komplett neu — liest Policies → Action IDs → Alias-Name → aufgelöste URL |
| `fetchPolicy(ServerConfig, String)` | neu | `GET /policies/{policyId}` |
| `parseEnforcementObjectIds(String)` | neu | Extrahiert `enforcementObjectId`-Werte aus Policy-Body |
| `fetchPolicyActions(ServerConfig, List<String>)` | neu | `GET /policyActions?policyActionIds=...` (Bulk) |
| `parseRoutingAliasName(String)` | neu | Findet `straightThroughRouting`-Block → Alias-Name per Regex |
| `parsePolicies(String)` | neu | Liest `api.policies[]` aus API-Detail-Body |
| `resolveAlias(ServerConfig, String)` | geändert | Ruft `GET /alias` auf, filtert per Name statt per ID-Pfad |
| `parseEndPointURIByName(String, String)` | neu | Sucht Alias-Eintrag per `"name"` in der Alias-Listen-Antwort |
| `parseNativeEndpoints()` | geändert | `dummy.dummy`-Sonderbehandlung entfernt |

### Pseudocode `getNativeEndpoints()`

```
body = GET /apis/{apiId}
policyIds = parsePolicies(body)
if policyIds leer: return []

actionIds = []
for policyId in policyIds:
    policyJson = fetchPolicy(server, policyId)       // GET /policies/{id}
    actionIds += parseEnforcementObjectIds(policyJson)

if actionIds leer: return []

policyActionsJson = fetchPolicyActions(server, actionIds)  // GET /policyActions?...
aliasName = parseRoutingAliasName(policyActionsJson)       // templateKey=="straightThroughRouting"
if aliasName == null: return []

resolvedUrl = resolveAlias(server, aliasName)              // GET /alias → filter by name
return [RoutingEndpoint.alias(aliasName, resolvedUrl)]
```

### HTTP-Calls pro API

#### Vor Alias-Cache-Optimierung
- `GET /apis/{apiId}` — 1×
- `GET /policies/{policyId}` — 1× pro Policy-ID
- `GET /policyActions?policyActionIds=...` — 1× (Bulk)
- `GET /alias` — **1× pro API** ← ineffizient bei N APIs

Bei 200 APIs: **800 HTTP-Calls**, davon 200× dieselbe vollständige Alias-Liste

#### Nach Alias-Cache-Optimierung
- `GET /apis/{apiId}` — 1×
- `GET /policies/{policyId}` — 1× pro Policy-ID
- `GET /policyActions?policyActionIds=...` — 1× (Bulk)
- `GET /alias` — **1× pro Server pro Session** (In-memory Cache)

Bei 200 APIs: **603 HTTP-Calls** — 199 `GET /alias`-Calls gespart

### Alias-Cache (In-memory, pro Server)

`AgwApiService` hält einen `Map<String, Map<String, String>> aliasCache`:
- Outer key: `baseUrl` des Servers (z. B. `https://vm40757:5559`)
- Inner map: `aliasName → endPointURI`

Beim ersten `resolveAlias()`-Aufruf pro Server wird `GET /alias` einmalig aufgerufen,
der komplette Response per `parseAllAliases()` in die Inner Map geparst und gecacht.
Alle weiteren Aufrufe lesen direkt aus dem Cache — kein HTTP-Call.

**Lebensdauer:** Session (bis Programmende). Passt zu bestehender `AgwApiService`-Instanz
in `InteractiveMenu`. Keine DB-Änderung, kein neues Config-Flag.

#### Neue Methoden

| Methode | Aufgabe |
|---|---|
| `loadAllAliases(ServerConfig)` | `GET /alias` → befüllt `aliasCache` für diesen Server |
| `parseAllAliases(String)` | Parst alle `{name, endPointURI}`-Blöcke aus dem Alias-Listen-Body in eine Map |

---

## Tests in `AgwApiServiceTest.java` (alle grün, erweitert)

| Test | Prüft |
|---|---|
| `parseEnforcementObjectIdsExtracts()` | Mehrere `enforcementObjectId`-Werte aus Policy-Body |
| `parseEnforcementObjectIdsEmptyWhenAbsent()` | Kein `enforcementObjectId` → leere Liste |
| `parseEnforcementObjectIdsNullInput()` | `null`-Input → leere Liste |
| `parsePoliciesExtractsPolicyIds()` | `api.policies[]` → ID-Liste |
| `parsePoliciesEmptyWhenAbsent()` | Kein `policies`-Feld → leere Liste |
| `parseRoutingAliasNameExtractsFromExpression()` | `${AKOS_API_EndpointAlias}/...` → `"AKOS_API_EndpointAlias"` |
| `parseRoutingAliasNameSimpleExpression()` | `${MyAlias}` → `"MyAlias"` |
| `parseRoutingAliasNameAbsent()` | Kein `straightThroughRouting`-Block → `null` |
| `parseRoutingAliasNameIgnoresNonRoutingActions()` | Mehrere Actions → nur `straightThroughRouting` ausgewertet |
| `parseRoutingAliasNameNullInput()` | `null`-Input → `null` |
| `parseEndPointURIByNameFound()` | Alias per Name aus Alias-Liste gefunden |
| `parseEndPointURIByNameNotFound()` | Alias nicht in Liste → `null` |
| `parseEndPointURIByNameNullInput()` | `null`-Input → `null` |
| `parseAllAliasesBuildsMap()` | Alias-Listen-Body → vollständige Name→URL Map |
| `parseAllAliasesSkipsNonEndpointAliases()` | Aliases ohne `endPointURI` landen nicht in der Map |
| `resolveAliasUsesCacheOnSecondCall()` | `GET /alias` wird nur 1× aufgerufen, zweiter Call kommt aus Cache |
| `getNativeEndpointsAlwaysUsesRoutingPolicy()` | Integration: dummy.dummy in nativeEndpoint irrelevant, Routing Policy liefert richtigen Endpoint |
| `getNativeEndpointsReturnsEmptyWhenNoPolicies()` | API ohne `policies`-Feld → leere Liste |
| `parseNativeEndpointsNoDummyDummyFallback()` | Regressionstest: `dummy.dummy` wird nicht mehr auf `aliasName` umgeschrieben |

---

## Dateien betroffen

| Datei | Änderungsart |
|---|---|
| `AgwApiService.java` | `getNativeEndpoints()` neu; `resolveAlias()` mit In-memory Cache; `loadAllAliases()` + `parseAllAliases()` neu; 6 weitere neue Methoden; `dummy.dummy`-Logik entfernt |
| `AgwApiServiceTest.java` | 19 neue/angepasste Tests; 2 alte `dummy.dummy`-Tests ersetzt |
| `InteractiveMenu.java` | **keine Änderung** |
| `RoutingEndpoint.java` | **keine Änderung** |
| `ApiDatabase.java` | **keine Änderung** |

---

## Akzeptanzkriterien (aus Issue)

- [x] APIs mit Routing Policy liefern nicht mehr `dummy.dummy`
- [x] Die Routing Policy wird über die referenzierten Policy-IDs ermittelt
- [x] Der Endpoint Alias wird aus der Routing Policy gelesen
- [x] Der tatsächliche Endpoint wird über den Alias aufgelöst
- [x] Endpoint-Listing verwendet den aufgelösten Endpoint
- [x] Endpoint-Check verwendet denselben aufgelösten Endpoint
- [x] Tests für den Fall `nativeEndpoint = dummy.dummy` + gültige Routing Policy

---

## Erkenntnisse aus Live-Tests (gegen echten Server)

| Annahme im Plan | Realität |
|---|---|
| `api.policies[]` enthält Policy Action IDs | ❌ enthält Policy-IDs — zweistufiger Lookup nötig |
| `GET /policyActions?policyActionIds=...` direkt verwendbar | ❌ erst `GET /policies/{id}` → `enforcementObjectId` → dann Bulk-Call |
| `GET /alias/{name}` auflösbar per Name als Pfad | ❌ Server interpretiert Pfad als UUID → `GET /alias` + Filter nach `"name"` |
| 1 Bulk-Call für Alias-Auflösung | ✅ `GET /alias` liefert alle Aliases in einem Call |
