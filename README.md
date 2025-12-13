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

### Fresh Contact Data Integration
TODO: clean up these instructions after testing, AI generated

This provider now supports fetching fresh contact information from ScoutNet without storing it in Keycloak, ensuring data privacy and freshness.

### What's Stored vs. What's Fresh

**Stored in Keycloak (minimal):**
- Basic identity: name, email, date of birth
- ScoutNet member number and token
- User roles (for authorization)
- Primary group membership

**Fetched fresh from ScoutNet:**
- Contact information (addresses, phone numbers)
- Profile images
- Detailed membership data

### Client Integration

#### Option 1: Direct ScoutNet API Access (Recommended)

Clients can use the stored `scoutnet_token` to fetch fresh data directly:

```javascript
// Get user info from Keycloak
const userInfo = await fetch('/auth/realms/master/protocol/openid-connect/userinfo', {
  headers: { Authorization: `Bearer ${keycloakAccessToken}` }
});
const user = await userInfo.json();

// Use ScoutNet token for fresh contact data
if (user.scoutnet_token) {
  const contactData = await fetch('https://scoutnet.se/api/get/profile', {
    headers: { Authorization: `Bearer ${user.scoutnet_token}` }
  });
  const profile = await contactData.json();
  console.log('Fresh addresses:', profile.addresses);
}
```

#### Option 2: Server-side Utility (Java)

For server-side applications:

```java
ScoutnetUserAttributeProvider provider = new ScoutnetUserAttributeProvider();
ContactInfo contactInfo = provider.getFreshContactInfo(user);
if (contactInfo != null) {
    Map<String, Address> addresses = contactInfo.getAddresses();
    // Use fresh address data
}
```

### Keycloak Configuration

#### 1. Authentication Flow Setup
1. Login to Keycloak Admin Console
2. Go to **Authentication** → **Flows**
3. Create a copy of the "Browser" flow named "ScoutID Browser"
4. Remove: Kerberos, Identity Provider Redirector
5. Add **Scoutnet Password Authenticator** as **Alternative**
6. Bind this flow as the default **Browser Flow**
7. **Important**: Set **security-admin-console** client's Browser Flow to "browser" to maintain admin access

#### 2. User Profile Configuration
1. Go to **Realm Settings** → **User Profile** → **JSON Editor**
2. Add ScoutNet attributes to make them visible:

```json
{
  "attributes": [
    {
      "name": "scoutnet_member_no",
      "displayName": "ScoutNet Member Number",
      "validations": {},
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin"]
      }
    },
    {
      "name": "scoutnet_dob",
      "displayName": "Date of Birth",
      "validations": {},
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin"]
      }
    },
    {
      "name": "roles",
      "displayName": "ScoutNet Roles",
      "validations": {},
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin"]
      },
      "multivalued": true
    }
  ]
}
```

#### 3. Client Scope for Roles (Optional)
1. Go to **Client Scopes** → **Create**
2. Name: `scoutnet-roles`
3. Add **User Attribute** mapper:
   - Name: `roles-mapper`
   - User Attribute: `roles`
   - Token Claim Name: `roles`
   - Claim JSON Type: `JSON`
   - Multivalued: `On`


## Using scoutid as sub
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
