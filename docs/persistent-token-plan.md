# Persistent Scoutnet Tokens — Implementation Plan

## Problem

Currently, when a user logs in, we get a **temporary** Scoutnet token (expires in 10 min) because we don't pass `app_id`. Keycloak's SSO cookie keeps the user logged in, but when the session expires, they must re-enter credentials. More importantly, during the SSO session, we serve potentially stale profile data since we only fetch from Scoutnet at login time.

If we extend Keycloak session cookies beyond a day or two, we risk delivering stale data to downstream services. We need to control the re-authentication flow ourselves so that cookie-based logins also fetch and update data from Scoutnet.

## Phase 1: Persistent tokens for cookie-based login ✅

Completed. The cookie authenticator validates the SSO cookie itself via `AuthenticationManager.authenticateIdentityCookie`, replacing Keycloak's built-in Cookie authenticator. This also covers Phase 2 (below).

### Step 1: Persistent token acquisition ✅

- Modify `ScoutnetClient.authenticate()` to accept optional `app_id`, `app_name`, `device_name` params
- Per the Scoutnet auth API: passing `app_id` (10+ chars) makes the token non-expiring; omitting it gives a 10-minute token
- `app_id`: `"scoutid-keycloak-{realmName}"` — ties the token to the Keycloak realm
- `app_name`: `"ScoutID"`
- `device_name`: Keycloak server URL (e.g. `https://id.example.se`)
- Add `ScoutnetClient.refreshToken(token, correlationId)` for `/api/refresh_token`

### Step 2: Secure token storage via CredentialModel ✅

- Create `ScoutnetTokenCredentialProvider` implementing `CredentialProvider<CredentialModel>`
  - Custom credential type: `"scoutnet-token"`
  - `secretData`: the persistent JWT token (encrypted at rest by Keycloak's credential store)
  - `credentialData`: metadata — `app_id`, creation timestamp
- Register via `META-INF/services/org.keycloak.credential.CredentialProviderFactory`
- In `ScoutnetAuthenticator.action()`, after successful login, store/update the token via this provider (not as a user attribute)

### Step 3: Cookie-based re-authentication authenticator ✅

- Created `ScoutnetCookieAuthenticator` implementing `Authenticator`
  - Validates the SSO cookie directly via `AuthenticationManager.authenticateIdentityCookie` (replaces Keycloak's built-in Cookie authenticator)
  - Placed **before** the password authenticator in the auth flow
  - On `authenticate()`: if cookie is valid, look up the user, retrieve their stored `scoutnet-token` credential
  - Use the persistent token to fetch fresh profile + roles from Scoutnet (throttled by configurable interval, default 60 min)
  - If token works → full profile sync (same as password login, including hash comparison and group sync), call `context.success()`
  - If token is expired/revoked → try `refreshToken()` → if that also fails, fall through to password authenticator via `context.attempted()`
  - Persistent tokens are only stored when "Remember Me" is checked (integrates with Keycloak's remember-me session lifetime)
- Created `ScoutnetCookieAuthenticatorFactory` to register it

### Step 4: Auth flow configuration ✅

The Keycloak browser flow (no built-in Cookie authenticator needed):

1. **ScoutnetCookieAuthenticator** (ALTERNATIVE) — validates SSO cookie, re-fetches data with persistent token
2. **ScoutnetAuthenticator** (ALTERNATIVE) — fallback to username+password form

### Step 5: Tests & docs ✅

- Integration tests for `authenticateWithAppId` + `refreshToken` against real Scoutnet
- Updated README with new flow setup instructions and debug logging guide
- Updated project-context.md with new architecture

## Phase 2: Replace Keycloak's built-in cached login ✅

Completed as part of Phase 1. The `ScoutnetCookieAuthenticator` validates the SSO cookie directly and always fetches fresh data from Scoutnet (throttled by configurable interval). No built-in Cookie authenticator is used.

## Phase 3: External identity provider login with persistent token (future)

Build a new login feature where users can authenticate via an external identity provider (e.g. Swedish BankID) that is connected to their Scoutnet account. The flow:

1. User authenticates with BankID (or other external provider)
2. Keycloak matches the external identity to an existing Scoutnet user
3. The stored persistent Scoutnet token is used to fetch fresh profile data
4. User gets a full Keycloak session with up-to-date Scoutnet data

The first implementation will be **Swedish BankID** — users can choose to connect BankID to their ScoutID profile, enabling passwordless login while still fetching correct membership data via the persistent token.

## Phase 4: Parental login — multiple accounts per external identity (future)

Extend the external provider login so that a single BankID (or other external identity) can be linked to **multiple** Scoutnet accounts. This enables "parental login" where a parent authenticates once with BankID and can then select which child's account to access. The persistent token for each linked account is used independently to fetch the correct profile data.
