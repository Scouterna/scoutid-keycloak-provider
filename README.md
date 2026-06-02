<div>
  <img align="right" height="40" src="./docs/scoutid.png" alt="ScoutID Logo">

  <h1>ScoutID Keycloak Provider</h1>
</div>

> [!TIP]
> This repo is part of a family:
> - [scoutid-keycloak](https://github.com/Scouterna/scoutid-keycloak)
> - [scoutid-keycloak-provider](https://github.com/Scouterna/scoutid-keycloak-provider) (this repo)
> - [scoutid-keycloak-theme](https://github.com/Scouterna/scoutid-keycloak-theme)
> - [scoutid-keycloak-infra](https://github.com/Scouterna/scoutid-keycloak-infra) (private)

This repository contains a custom provider for Keycloak to sign in with ScoutID,
the Guides and Scouts of Sweden's membership system.

If you want to setup a client, follow instructions in docs/client_config_guide.md.

## Running a local Keycloak instance

This repo contains a Docker Compose project to quickly spin up a local Keycloak
instance. Make sure you've got Docker installed.

```bash
# Generate certificates to run Keycloak in HTTPS mode. You should only need to do this once.
./generate-certs.sh

# Fetch the ScoutID theme
curl -fsSL -o keycloak-theme-for-kc-all-other-versions.jar https://github.com/Scouterna/scoutid-keycloak-theme/releases/latest/download/keycloak-theme-for-kc-all-other-versions.jar

# Start Keycloak
docker compose up

# If you want you can run it in the background (daemonized) using the -d flag
docker compose up -d

# And to stop it if you're running it in the background
docker compose down
```

## Compiling the provider package

```bash
./mvnw clean package
```

The local Docker setup picks up the jar automatically via the volume mount in `docker-compose.yml`. For a standalone Keycloak deployment, copy the jar from `target/` to the `providers/` directory and restart Keycloak.

## Using the scoutnet based custom authentication

`docker compose up` will start Keycloak and automatically apply all configuration via [keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli). This sets up the ScoutID authentication flow, all client scopes with mappers, and a local test client — no manual steps needed.

You can verify login at http://localhost:8080/realms/master/account once Keycloak is ready. The admin console at http://localhost:8080/admin/ keeps its own flow override so admin/admin still works there.

The configuration is split by concern under `keycloak-config/`:
- `realm.yaml` — token lifetimes and login settings
- `authentication.yaml` — ScoutID browser flow (Cookie Re-authenticator → Password Authenticator)
- `scopes.yaml` — client scope definitions with all mappers
- `clients.yaml` — local test client (`scout-test-client`)


### Claim overview

| Scope | Claims |
|-------|--------|
| `openid` | `sub`, `preferred_username` (`scoutnet-<member_no>`) |
| `profile` | `name`, `given_name`, `family_name`, `picture`, `birthdate`, `locale`, `scoutnet_member_no` |
| `email` | `email`, `email_verified`, `scouterna_email`, `alt_email` |
| `phone` | `phone_number` |
| `scoutnet-memberships` | `primary_group_name`, `primary_group_no`, `memberships`, `group_emails_json` |
| *(stored, not exposed)* | `firstlast` (used to derive group email addresses), `scoutnet_profile_hash` (change detection) |

#### The `memberships` claim

`memberships` is a JSON object keyed by entity type. Each type maps entity IDs (strings) to an entry object:

| Entity type | Entry fields |
|-------------|-------------|
| `groups` | `name`, `is_primary`, `roles[]` |
| `troops` | `name` *(if known)*, `groupId` *(int, null if unknown)*, `roles[]` |
| `patrols` | `name` *(if known)*, `groupId` *(int, null if unknown)*, `roles[]` |
| `organisations`, `regions`, `districts`, `corps`, `networks`, `projects` | `roles[]` |

Each role is `{"id": <int>, "key": "<string>", "name": "<string>"}`. Display name (`name`) is only populated for group-level roles where a translation is available from Scoutnet.

See config_support/access_token_example.json for a full example.

### Using scoutid as sub

For some clients a predictable `sub` is needed — for example to pre-populate members before first login, or for compatibility with other login methods. Note that this can cause problems if you later want to support combined login methods.

1. Go to **Clients** → your client → **Client scopes** → `[client-name]-dedicated`
2. **Configure a new mapper** → **User Attribute** and set:

| Field | Value |
|-------|-------|
| Mapper type | User Attribute |
| Name | `sub member_no mapper` |
| User Attribute | `scoutnet_member_no` |
| Token Claim Name | `sub` |
| Claim JSON Type | String |
| Add to ID token | On |
| Add to access token | On |
| Add to lightweight access token | On |
| Add to userinfo | On |
| Add to token introspection | On |
| Multivalued | Off |
| Aggregate attribute values | Off |

## Debugging and Development

### Debugging authentication issues

1. **Run unit tests** to verify consistent handling:
   ```bash
   # Run all unit tests
   ./mvnw test
   ```

2. **Run integration tests** to verify basic functionality:
   ```bash
   # Set credentials and run tests
   export SCOUTNET_USERNAME=your-personnummer-or-email
   export SCOUTNET_PASSWORD=your-scoutnet-password
   export SCOUTNET_BASE_URL=https://scoutnet.se # Optional, defaults to https://scoutnet.se
   ./mvnw verify
   ```
   This will test the core authentication logic without requiring a full Keycloak setup.

3. **Test with full local Keycloak setup**:
   ```bash
   # Compile the provider
   ./mvnw clean package
   
   # Start Keycloak with your compiled provider
   docker compose up
   ```
   Then test login at: http://localhost:8080/realms/master/account

4. **Check Keycloak logs** for correlation IDs and error details:
   ```bash
   docker compose logs -f keycloak
   ```

5. **Enable debug logging** for the ScoutID provider via `KC_LOG_LEVEL` in `docker-compose.yml`:
   ```yaml
   KC_LOG_LEVEL: INFO,se.scouterna.keycloak:DEBUG
   ```
   This is the default — DEBUG only for the ScoutID provider, INFO for everything else. For full Keycloak debug logging use `KC_LOG_LEVEL: DEBUG` (very verbose).

   At **INFO** level (default), you will see: login success/failure, first-time user creation, and profile data updates.
   At **DEBUG** level, you will additionally see: cookie validation details, fetch throttle decisions with timestamps, remember-me status, profile hash comparisons, and token storage events.

6. **Common error patterns**:
   - `404 Not Found`: API endpoint doesn't exist (check SCOUTNET_BASE_URL)
   - `invalidUserMessage`: Wrong credentials or user not found
   - `loginTimeout`: Service unavailable or network issues

### VS Code Setup

1. **Install required extensions**:
   - [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) (includes Language Support, Debugger, Test Runner, Maven, and Project Manager)
   - [Docker](https://marketplace.visualstudio.com/items?itemName=ms-azuretools.vscode-docker) (for managing Docker Compose)

2. **Open the project**:
   ```bash
   code scoutid-keycloak-provider
   ```

3. **Configure Java**:
   - Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on Mac)
   - Type "Java: Configure Java Runtime"
   - Set Java 21 as the project JDK

4. **Verify setup**:
   - Open `src/main/java/se/scouterna/keycloak/ScoutnetAuthenticator.java`
   - Check that there are no red error underlines
   - The status bar should show "Java 21" in the bottom right

5. **Run tests in VS Code**:
   - Open the Test Explorer (Testing icon in sidebar)
   - Set environment variables in `.vscode/settings.json`:
     ```json
     {
       "java.test.config": {
         "env": {
           "SCOUTNET_USERNAME": "your-personnummer-or-email",
           "SCOUTNET_PASSWORD": "your-scoutnet-password",
           "SCOUTNET_BASE_URL": "https://scoutnet.se"
         }
       }
     }
     ```
   - Click the play button next to `ScoutnetClientIT` to run integration tests

6. **Build and debug**:
   - Use `Ctrl+Shift+P` → "Java: Rebuild Projects" to compile
   - Set breakpoints by clicking in the gutter next to line numbers
   - Use F5 to start debugging tests

## Commits and releases

This repository uses
[release-please](https://github.com/googleapis/release-please) to manage
releases and all commits must therefore follow the [Conventional
Commits](https://www.conventionalcommits.org/) format.

Every commit pushed to the `main` branch will inspected by release-please.
Commits that cause a version bump will cause release-please to create or update
an existing release pull request. Merging this pull request will trigger a
release.

Immediately after a release is created, release-please will create a SNAPSHOT
pull request. We've configured a workflow to automatically merge this since our
main branch is also our development branch.

Once a release is created a workflow will automatically build the project and
publish the resulting jar to the GitHub Packages Maven registry and also add it
to the release assets.
