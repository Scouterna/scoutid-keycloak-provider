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
