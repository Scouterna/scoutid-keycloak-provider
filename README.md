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
docker compose -d

# And to stop it if you're running it in the background
docker compose down
```

## Compiling the provider package
run `./mvnw clean package`

Add the jar-file produced in `target/` to the `providers/` directory of the
Keycloak server and restart it

## Using the scoutnet based custom authentication
1. Login to your development keycloak instance using credentials admin:admin
2. Go to Authentication, make a duplicate of the browser flow name with name and description "ScoutID browser login"
3. Remove Kerberos, Identity Provider Redirector and Scoutid browser login forms
4. Add execution and choose Scoutnet Password Authenticator. Set it to Alternative
5. Choose action -> Bind flow and bind it to the Browser flow. Now it's the new default login method
6. Go to Clients -> security-admin-console -> Advanced -> Authentication flow overrides and set Browser Flow to browser. Save. Otherwise it will now be impossible to login to the keycloak admin interface.

You can now verify login using this test interface
http://localhost:8080/realms/master/account

In order to see our custom fields, you need to make them visible, and also include them in oidc response.
1. Go to Realm settings -> User profile -> JSON editor and replace the json with the content of config_support/user_profile.json
2. In order to include a field into a oidc response, enter Client scopes -> profile -> Mappers -> birthdate and set user attribute to scoutnet_dob
3. Repeat for the other fields as well.

### Enabling the ScoutID theme
Go into Realm settings, enter tab Theme and choose ScoutID.

### Using scoutid as sub
For some client applications, a known sub is needed to prepopulate members before they are created by keycloak, or for compitability with other login methods. The sub is the unique user id used by OIDC, by default created when a user is initialised in keycloak. Note that using scoutnet member number as sub can cause problems when using a combined login method (upcoming feature). If you want to use the scoutnet member id as sub:
1. Create the client
2. Under client scopes enter [client_name]-dedicated to change client specific default scope
3. Add mapper -> By configuration -> User attribute
4. Set as below:
Mapper type: User Attribute
Name: sub member_no mapper
User Attribute: scoutnet_member_no
Token Claim Name: sub
Claim JSON Type: String
Add to ID token: On
Add to access token: On
Add to lightweight access token: On
Add to userinfo: On
Add to token introspection: On
Multivalued: Off
Aggregate attribute values: Off

## Debugging and Development

### Debugging authentication issues

1. **Run unit tests** to verify consistent handling:
   ```bash
   # Run all unit tests
   ./mvnw test

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
   using username: admin, password: admin

   If this is your first time running the docker container, you need to follow the [above setup instructions](#using-the-scoutnet-based-custom-authentication) first.

3. **Check Keycloak logs** for correlation IDs and error details:
   ```bash
   docker compose logs -f keycloak
   ```

4. **Enable debug logging** by adding to `docker-compose.yml`:
   ```yaml
   environment:
     KC_LOG_LEVEL: DEBUG
   ```

5. **Common error patterns**:
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
