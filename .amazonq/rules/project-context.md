# ScoutID Keycloak Provider — AI Context

## What this project is

A custom Keycloak authentication provider (Java 21, Keycloak 26.5.6) for **ScoutID**, the identity system of Guides and Scouts of Sweden (Scouterna). It authenticates users against the external **Scoutnet API** (scoutnet.se) and syncs their profile, roles, and group memberships into Keycloak.

This is an open-source project. The repo is public on GitHub under the Scouterna organization.

## Architecture overview

The provider is a single JAR deployed into Keycloak's `providers/` directory. There is no database of its own — all state lives in Keycloak's user/group model.

### Authentication flow
The auth flow consists of two authenticators in sequence (both ALTERNATIVE):

**ScoutnetCookieAuthenticator** (runs first):
1. Validates the Keycloak SSO cookie via `AuthenticationManager.authenticateIdentityCookie`
2. If valid and a persistent Scoutnet token is stored → fetches fresh profile/roles (throttled by configurable interval, set to 0 to always fetch)
3. Syncs data into Keycloak (skipped if profile hash unchanged)
4. If anything fails → falls through to password authenticator

**ScoutnetAuthenticator** (password fallback):
1. User submits personnummer (Swedish national ID), member no or email + password
2. Input is normalized (personnummer → 12-digit format)
3. `ScoutnetClient.authenticate()` → POST to Scoutnet `/api/authenticate` → returns a bearer token
4. If "Remember Me" is checked: requests a persistent token (via `app_id`) and stores it in Keycloak's credential store
5. Token is used to GET `/api/get/profile` (user data) and `/api/get/user_roles` (roles)
6. Keycloak user is created (username: `scoutnet|{member_no}`) or found
7. Profile data is synced to Keycloak user attributes if a SHA-256 hash of the profile+roles data has changed
8. Group memberships are synced under a `scoutnet` parent group by `ScoutnetGroupManager`

### Key classes
- `ScoutnetAuthenticator` — Password authenticator, handles login form + user creation/update
- `ScoutnetCookieAuthenticator` — Cookie-based re-authenticator, fetches fresh data using stored persistent token
- `ScoutnetProfileSync` — Shared logic for fetching profile/roles and syncing into Keycloak (used by both authenticators)
- `ScoutnetTokenCredentialProvider` — Keycloak credential SPI for securely storing persistent Scoutnet tokens
- `ScoutnetAuthenticatorFactory` / `ScoutnetCookieAuthenticatorFactory` — Keycloak SPI factories
- `ScoutnetClient` — HTTP client for Scoutnet API (shared static HttpClient, HTTP/2)
- `ScoutnetGroupManager` — Syncs Keycloak groups from Scoutnet memberships and roles
- `client/dto/*` — Jackson-annotated DTOs mapping Scoutnet API responses

### User attributes stored in Keycloak
`scoutnet_member_no`, `scoutnet_dob`, `scoutnet_primary_group_name`, `scoutnet_primary_group_no`, `scoutid_local_email` ({member_no}@scoutid.local), `scouterna_email`, `picture`, `firstlast` (firstname.lastname normalized for email), `roles` (multivalued, format: `type:typeId:roleName`), `group_email_{groupId}` (per-group email like firstname.lastname@domain), `scoutnet_profile_hash`, `scoutnet_last_fetch` (epoch millis, used for fetch throttling)

### Persistent token storage
When "Remember Me" is used, a persistent Scoutnet API token is stored via Keycloak's `CredentialModel` (type: `scoutnet-token`). The token is stored in `secretData` (plain text in the database, same as OTP secrets and other non-password Keycloak credentials) with metadata in `credentialData`. This is used by the cookie authenticator to fetch fresh data without requiring password re-entry. Database-level access controls and disk encryption in production are the expected protection — Keycloak does not encrypt `secretData` at rest for custom credential types.

### Group structure
Groups live under a `scoutnet` parent group. Subgroups are named by their Scoutnet group ID (e.g. `766`). Each subgroup can have a `domain` attribute (e.g. `example.se`) used to generate `group_email_` user attributes. The `domain` attribute is admin-configured in Keycloak, not from Scoutnet.

### Profile hash optimization
Profile updates are skipped when a SHA-256 hash (of profile JSON + roles JSON + provider version + group domain attributes, excluding `last_login`) hasn't changed since last login.

## Build and test

- Build: `./mvnw clean package`
- Unit tests: `./mvnw test`
- Integration tests (require real Scoutnet credentials): `./mvnw verify` with env vars `SCOUTNET_USERNAME`, `SCOUTNET_PASSWORD`
- Local Keycloak: `docker compose up` (requires `./generate-certs.sh` first run)
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/). Releases are managed by release-please.

### Logging

At **INFO** level (default), the provider logs: login success/failure, first-time user creation, profile data updates (hash changed), and token/refresh failures.

At **DEBUG** level, it additionally logs: cookie validation details, fetch throttle decisions with timestamps and remember-me status, profile hash comparisons (unchanged), token storage events, and form display/submission.

To enable provider-specific debug logging without flooding Keycloak's own logs, set:
```
KC_LOG_LEVEL=INFO,se.scouterna.keycloak:DEBUG
```

## Security practices

- **Never hardcode credentials, tokens, or secrets.** Scoutnet credentials come from user input at login time. Persistent tokens are stored via Keycloak's credential store (plain text in the database, protected by database-level access controls — same model as OTP secrets and WebAuthn credentials in Keycloak), never as user attributes. In production, use an encrypted database backend (e.g. PostgreSQL with disk encryption). `SCOUTNET_BASE_URL` is the only env var used by the provider itself.
- **Never log passwords or tokens.** Correlation IDs are used for tracing. Personnummer is masked in logs (only birthdate portion shown). The `ScoutnetClient` accepts a separate `logUsername` parameter to ensure raw personnummer never reaches log output. Emails and member numbers may be logged on auth failure, but never passwords or bearer tokens.
- **Validate and sanitize all external input.** Personnummer normalization, domain validation (`isValidDomain`), and name formatting (`formatNameForEmail`) all strip or reject unexpected characters.
- **Never expose internal error details to users.** `ErrorResponse.getSafeErrorMessage()` truncates messages. User-facing errors use Keycloak message keys, not raw API responses.
- **Treat Scoutnet API responses as untrusted.** All DTOs use `@JsonIgnoreProperties(ignoreUnknown = true)`. Null checks are applied before using profile/roles data.
- **Keep test credentials out of source.** Integration tests read from env vars, never from committed files. `.vscode/` is gitignored.

## Workflow instructions

- **Before writing code**: Always clarify requirements and confirm the approach. Ask questions if anything is ambiguous. Do not assume.
- **Only the user decides when we're done.** Never assume a task is finished or offer a commit message unless the user explicitly says we're done or finished.
- **When the user says we're done or finished**: Read through all changes made during the session. Verify:
  1. No work-in-progress code, TODO comments, or placeholder logic left behind
  2. No security vulnerabilities introduced (credential leaks, unsanitized input, exposed internals)
  3. No ambiguous or misleading comments
  4. Tests still pass conceptually (no broken imports, missing methods, etc.)
  5. Changes are consistent with the existing code style and architecture
  6. This context file (`project-context.md`) and `README.md` are still accurate after the changes — update them if needed
  Then provide a suggested commit message following Conventional Commits format. Example:
  ```
  feat: add support for troop-level group sync

  Sync troop groups from Scoutnet roles into Keycloak subgroups
  under the scoutnet parent group.
  ```
  Use `feat:` for new features, `fix:` for bug fixes, `docs:` for documentation, `refactor:` for restructuring, `perf:` for performance. Add `!` after the type for breaking changes (e.g. `feat!:`).
