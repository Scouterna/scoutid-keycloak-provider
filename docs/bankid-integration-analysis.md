# BankID Integration Analysis for ScoutID

> Analysis date: 2025-07-14
> Context: Evaluating options for adding BankID authentication to the ScoutID Keycloak setup

## Background

ScoutID currently authenticates users against the Scoutnet API via a custom Keycloak `Authenticator` SPI plugin. The goal is to add BankID as an additional login method, allowing users to authenticate with BankID and be linked to their existing Scoutnet profile.

Two open-source BankID implementations were evaluated, plus the option of writing a custom integration.

---

## Option A: bankid4keycloak (Recommended)

**Repository:** [sweid4keycloak/bankid4keycloak](https://github.com/sweid4keycloak/bankid4keycloak)
**License:** Apache 2.0
**Maintainer:** Pontus Ullgren + ~10 community contributors
**Last activity:** December 2025 (active, tracks Keycloak releases)

### What it is

A native Keycloak Identity Provider plugin. A single JAR deployed into Keycloak's `providers/` directory — architecturally identical to the ScoutID provider.

### Architecture

- Implements `IdentityProviderFactory` / `AbstractIdentityProvider` Keycloak SPIs
- `SimpleBankidClient` talks directly to the BankID RP API v6.0 via Apache HttpClient (Keycloak's own `HttpClientBuilder` with mutual TLS)
- Session state stored in Keycloak's Infinispan `ACTION_TOKEN_CACHE`
- UI via FreeMarker templates (`login-bankid.ftl`, `start-bankid.ftl`) using Keycloak's theme system
- QR code generation with animated HMAC-based codes (per BankID spec)
- ZXing library shaded into the JAR for QR rendering

### Authentication flow

```
User → Keycloak login → "Login with BankID" button
  → BankidEndpoint /start → (optional personnummer input) → /login
  → SimpleBankidClient POST /rp/v6.0/auth → BankID API
  → User opens BankID app (QR scan or autostart link)
  → Client-side JS polls /collect every 2s
  → SimpleBankidClient POST /rp/v6.0/collect → BankID API
  → On complete: /done extracts personnummer, name from CompletionData
  → BrokeredIdentityContext created → Keycloak first-broker-login flow
  → User linked to existing scoutnet|{member_no} account
```

### Strengths

- **Zero extra infrastructure** — runs inside Keycloak, no separate container, no Redis, no SAML
- **~0 MB additional RAM** — shares Keycloak's JVM
- **Keycloak-native theming** — FreeMarker templates inherit from `template.ftl`, so the ScoutID Keycloak theme applies automatically to BankID login pages
- **Same tech stack** as ScoutID provider — Java 21, Keycloak 26.x SPIs, no Spring dependencies
- **Simple deployment** — one additional JAR in `providers/`
- **Direct BankID API integration** — no SAML round-trip overhead
- **Active maintenance** — regular Keycloak version bumps, recent production bug fixes (HTTP client thread leak)
- **Configurable via Keycloak admin UI** — API URL, keystore, truststore, QR toggle, personnummer requirement, hashed storage

### Concerns

- **No unit tests** — the project has zero test coverage
- **TODO in collect handling** — `SimpleBankidClient.sendCollect()` has `// TODO: Handle when status is failed`
- **Inline CSS in templates** — functional but not clean; would need rework if deep theming customization is needed
- **Infinispan cache usage** — uses `ACTION_TOKEN_CACHE` directly, which could be fragile across major Keycloak upgrades
- **Basic error handling** — `BankidClientException` wraps hint codes but doesn't distinguish all failure modes granularly
- **Client-side polling** — `bankid.js` uses XMLHttpRequest polling, functional but dated
- **No cancel-on-timeout** — if user abandons, the BankID order expires after 3 minutes server-side
- **Community project** — not backed by a government agency or large organization

### Deployment

```yaml
# docker-compose.yml — just add the JAR
volumes:
  - ./bankid4keycloak.jar:/opt/keycloak/providers/bankid4keycloak.jar
  - ./bankid-config/keystore.p12:/tls/keystore.p12:ro
  - ./bankid-config/truststore.p12:/tls/truststore.p12:ro
```

### What we'd need to build

A custom **first-broker-login flow** or `IdentityProviderMapper` that:
1. Extracts the personnummer from the BankID identity
2. Looks up the Scoutnet user (by personnummer → member number mapping)
3. Links the BankID identity to the existing `scoutnet|{member_no}` Keycloak user

This linking code would live in the ScoutID provider repo.

---

## Option B: bankid-saml-idp (Sweden Connect)

**Repository:** [swedenconnect/bankid-saml-idp](https://github.com/swedenconnect/bankid-saml-idp)
**License:** Apache 2.0
**Maintainer:** Sweden Connect / DIGG (Swedish Agency for Digital Government), developed by IDsec Solutions AB
**Last activity:** Active, version 1.3.0

### What it is

A standalone SAML Identity Provider for BankID, built for the Sweden Connect federation and the Swedish eID Framework. It's a full Spring Boot application, not a Keycloak plugin.

### Architecture

- **Three modules:**
  - `bankid-api` — Java client library for BankID RP API (Spring WebFlux/WebClient, reactive)
  - `bankid-frontend` — Vue.js SPA for the BankID UI (QR code, device selection, status)
  - `bankid-idp` — Spring Boot application combining the above into a SAML IdP
- Requires its own Docker container, TLS certificates, and optionally Redis for session management
- Speaks SAML with Swedish eID Framework profiles
- Published to Maven Central as `se.swedenconnect.bankid:bankid-idp`

### Authentication flow

```
User → Keycloak login → "Login with BankID" button
  → Keycloak sends SAML AuthnRequest → BankID SAML IDP (separate service)
  → Vue.js frontend shows QR code / device selection
  → bankid-rp-api POST /rp/v6.0/auth → BankID API
  → User opens BankID app
  → Polling via REST API between frontend and backend
  → On complete: SAML Response with personnummer in assertion
  → Back to Keycloak → first-broker-login flow
  → User linked to existing scoutnet|{member_no} account
```

### Strengths

- **Government-backed** — developed by DIGG, the Swedish digital government agency
- **Battle-tested** — designed for the Sweden Connect federation, used in production by government services
- **Comprehensive** — full SAML metadata, audit logging, health checks, circuit breakers (Resilience4j), Prometheus metrics
- **Well-documented** — extensive configuration docs, override system, API documentation
- **Flexible UI customization** — CSS overrides, message overrides, content overrides without rebuilding
- **bankid-rp-api** is a solid reference implementation of the BankID RP API

### Concerns

- **Heavy infrastructure** — separate Docker container (~300-500 MB RAM), optionally Redis
- **Java 25 requirement** — the project targets Java 25, ahead of Keycloak's Java 21
- **SAML complexity** — adds a full SAML round-trip (metadata exchange, XML signing, assertion parsing) for what is ultimately just identity verification
- **Separate theming** — Vue.js frontend has its own CSS system; Scouterna branding requires CSS overrides or a custom frontend build, not Keycloak theme integration
- **Spring Boot dependency tree** — Spring Boot 4, Spring 7, WebFlux, Reactor, OpenSAML, Redisson — massive dependency surface
- **Cannot be embedded** in a Keycloak provider JAR due to Spring dependencies
- **Overkill for our use case** — designed for multi-SP SAML federations, not single-Keycloak setups

### Theming approach

The BankID SAML IDP supports CSS overrides via a configuration directory:

```yaml
bankid:
  ui:
    override:
      directory-path: "/config/overrides"
    provider:
      svg-logotype: "file:/config/scoutid-logo.svg"
```

A CSS override file mapping Scouterna design tokens:

```css
:root {
  --bg-color: #f5f5f5;
  --fg-color: #25343f;           /* neutral-800 */
  --header-bg-color: #003660;    /* blue-700 */
  --btn-bg-color: #003660;
  --btn-fg-color: #ffffff;
  --link-color: #003660;
  --qr-corner-color: #003660;
}
```

This gets ~80% visual alignment without code changes, but the BankID pages will never look identical to the Keycloak-themed ScoutID pages.

### The bankid-rp-api sub-module

The `bankid-api/` module is a standalone Java library for the BankID RP API, published as `se.swedenconnect.bankid:bankid-rp-api` on Maven Central. It's well-written but depends on Spring WebFlux (`WebClient`, `Mono<>` reactive types), making it impractical to use directly in a Keycloak SPI plugin without pulling in Spring.

### Deployment

```yaml
services:
  keycloak:
    # ... existing config ...
  bankid-idp:
    image: swedenconnect/bankid-saml-idp:1.3.0
    ports:
      - "9443:8443"
      - "9444:8444"
    volumes:
      - ./bankid-config/bankid.yml:/config/bankid.yml:ro
      - ./bankid-config/overrides:/config/overrides:ro
      - ./bankid-config/credentials:/config/credentials:ro
    environment:
      SPRING_CONFIG_IMPORT: /config/bankid.yml
```

---

## Option C: Custom BankID authenticator (write our own)

Write a BankID authenticator from scratch as part of the ScoutID provider, using `java.net.http.HttpClient` (like the existing `ScoutnetClient`).

### Strengths

- Full control over the implementation
- Tightest possible integration with ScoutID (could combine Scoutnet lookup + BankID in one flow)
- No external dependencies beyond what we already have
- Could use the `bankid-rp-api` source as reference without depending on it

### Concerns

- **Significant development effort** — BankID integration involves:
  - Mutual TLS client certificate handling
  - Animated QR code generation (HMAC-SHA256 per second)
  - Async polling flow with timeout handling
  - All BankID hint codes and error states
  - FreeMarker templates for the UI
  - Infinispan or session-based state management for in-flight auth orders
- **Maintenance burden** — must track BankID API changes independently
- **Reinventing the wheel** — bankid4keycloak already solves this exact problem

### When this makes sense

Only if bankid4keycloak proves unreliable in practice and the SAML IDP approach is too heavy. The bankid4keycloak codebase is small enough (~800 lines of Java) that forking and maintaining it ourselves is feasible if needed.

---

## Comparison matrix

| Criteria | bankid4keycloak | bankid-saml-idp | Custom |
|---|---|---|---|
| **Infrastructure** | None (JAR in Keycloak) | Separate container + optional Redis | None (JAR in Keycloak) |
| **RAM overhead** | ~0 MB | ~300-500 MB | ~0 MB |
| **Java version** | 21 ✅ | 25 ⚠️ | 21 ✅ |
| **Keycloak theme** | Automatic ✅ | Separate CSS overrides | Automatic ✅ |
| **Deployment** | Drop JAR | Docker container + config | Drop JAR |
| **Maturity** | Community, active | Government-backed, production | New, untested |
| **Test coverage** | None ⚠️ | Has tests ✅ | We'd write them |
| **Dev effort** | Low (configure + linking code) | Medium (deploy + configure + theme) | High (full implementation) |
| **Maintenance** | Community updates | Sweden Connect updates | Fully on us |
| **BankID API** | Direct REST | Direct REST (via Spring) | Direct REST |
| **SAML overhead** | None | Full SAML round-trip | None |

---

## Recommendation

**Use bankid4keycloak (Option A)** as the starting point:

1. It's the right architecture — a native Keycloak IDP plugin, same as our ScoutID provider
2. Zero infrastructure overhead — no extra containers, no SAML, no Redis
3. Keycloak theming works automatically — ScoutID theme applies to BankID pages
4. The codebase is small (~800 lines Java) and auditable
5. Active community maintenance tracking Keycloak releases

**Mitigation for reliability concerns:**

- Audit the code before deploying (especially `SimpleBankidClient`, `BankidEndpoint`, and the Infinispan cache usage)
- Add tests for critical paths (auth flow, collect polling, error handling)
- The codebase is small enough to fork and maintain if the upstream project becomes inactive
- Consider contributing fixes upstream (the TODO in collect, error handling improvements)

**Required custom work regardless of option chosen:**

- A first-broker-login authenticator/mapper to link BankID personnummer → Scoutnet member → `scoutnet|{member_no}` Keycloak user
- BankID test RP certificate setup (available from bankid.com developer portal)
- Keycloak admin configuration (Identity Provider, first-broker-login flow)

**Keep bankid-saml-idp as reference:**

- The `bankid-rp-api` module is excellent reference material for BankID API integration patterns
- The Sweden Connect project's documentation on BankID configuration, error handling, and security is valuable regardless of which implementation we use
- If we ever need to join the Sweden Connect federation formally, the SAML IDP would become the right choice

---

## Next steps

1. Build and test bankid4keycloak against our Keycloak 26.5.1 instance
2. Audit the bankid4keycloak source for security and reliability
3. Design and implement the personnummer → Scoutnet user linking flow
4. Set up BankID test environment (test certificates from bankid.com)
5. Create Scouterna-branded FreeMarker template overrides if the default BankID templates need visual adjustments beyond what the Keycloak theme provides
