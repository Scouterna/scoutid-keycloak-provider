# ScoutID Keycloak

Denna instruktion riktar sig till dig som vill koppla in en klient på ScoutID.

## Om ScoutID Keycloak

ScoutID Keycloak är Scouternas centrala identitetshanteringssystem som använder Keycloak för att hantera inloggningssessioner, Single Sign-On (SSO) och OIDC/SAML-klienter. Systemet autentiserar användare genom Scoutnets API med hjälp av en anpassad Keycloak-provider.

**Viktiga länkar:**
- Admin-gränssnitt: [admin.dev.id.scouterna.se/admin/scoutnet/console/](https://admin.dev.id.scouterna.se/admin/scoutnet/console/)
- Testinloggning: [dev.id.scouterna.se/realms/scoutnet/account](https://dev.id.scouterna.se/realms/scoutnet/account)

Systemet möjliggör för Scouternas olika tjänster att använda samma inloggningsuppgifter (personnummer och lösenord från Scoutnet) utan att behöva hantera autentisering själva. Scoutkårer och andra delar av scoutrörelsen är också varmt välkomna.

## Autentisering och auktorisering

**Autentisering** handlar om att verifiera vem användaren är (personnummer + lösenord via Scoutnet).
**Auktorisering** handlar om vad användaren får göra (roller, behörigheter). Även de kontaktuppgifter som tillhandahålls av ScoutID följer samma mönster.

### Viktigt att tänka på vid klientkonfiguration

I ScoutID Keycloak lever inloggningssessioner i **30 dagar**, men tokens till Scoutnets API är endast giltiga i **10 minuter**. Detta innebär:

- **För SSO-applikationer**: Användaren behöver bara logga in en gång per 30 dagar
- **För auktorisering**: Roller och behörigheter kan vara inaktuella om sessionen är gammal

**Sessionstider och säkerhet:**
Du kan justera sessionstider per klient för att balansera användarvänlighet mot säkerhet:

- **Längre sessioner** (upp till 30 dagar): Bättre användarupplevelse, mindre frekventa inloggningar
- **Kortare sessioner** (10 minuter - 1 dag): Snabbare access-uppdateringar, bättre säkerhet

**Konfigurera sessionstid per klient:**
1. Gå till **Clients** → [klient-namn] → **Advanced Settings**
2. **Access Token Lifespan**: Sätt önskad tid (t.ex. `1 day` för känsliga applikationer)
3. **Client Session Idle**: Tid innan inaktiv session upphör
4. **Client Session Max**: Maximal sessionstid oavsett aktivitet

**Rekommendation**: För applikationer som kräver aktuell auktoriseringsinformation använd kortare sessionstider.

## Vanliga klienter

### Google Workspace

För Google Workspace rekommenderas att använda ScoutID för SSO-inloggning tillsammans med automatisk användarsynkronisering.

**I Google Admin Console:**
- Gå till Security > Authentication > SSO with third party IdP
- Använd Keycloak SAML metadata URL
- Ta rätt på SP details, där du behöver
  - Entity ID
  - ACS URL

**SSO-konfiguration:**
1. **Skapa ny klient** i Keycloak admin-konsolen
2. **Client ID**: Entity ID från ovan
3. **Client Protocol**: `saml`
4. **Valid Redirect URIs**: ACS URL från ovan.

Öppna klientinställningarna. 
1. Under Client scopes välj länken med ditt client id.
2. Configure a new mapper
3. User attribute for NameID
4. Sätt namn valfritt, Name ID Format till *:emailAddress och User Attribute till firstlast.



**Användarsynkronisering:**
För att automatiskt skapa och synkronisera användarkonton från Scoutnet till Google Workspace, använd [Google-Scoutnet-synk](https://github.com/Scouterna/Google-Scoutnet-synk).

Synkroniseringen:
- Skapar användarkonton på formen `fornamn.efternamn@domän.se`
- Synkroniserar personer från e-postlistor eller alla med avdelnings-/kårroller
- Inaktiverar konton som inte längre matchar
- Kräver ingen manuell lösenordshantering när ScoutID används för SSO

### Microsoft Teams / Office 365

För Microsoft-tjänster rekommenderas att använda ScoutID för SSO-inloggning tillsammans med automatisk användarsynkronisering.

**SSO-konfiguration:**
1. **Skapa ny klient** med protokoll `openid-connect`
2. **Client ID**: Använd Application ID från Azure AD
3. **Valid Redirect URIs**: `https://login.microsoftonline.com/common/oauth2/nativeclient`
4. **Scopes**: `openid profile email`

**Mappers som behövs:**
- `preferred_username` → email
- `given_name` → förnamn
- `family_name` → efternamn

**Användarsynkronisering:**
För att automatiskt skapa och synkronisera användarkonton från Scoutnet till Office 365, använd [Office365-Scoutnet-synk](https://github.com/Scouterna/Office365-Scoutnet-synk).

**Funktioner:**
- **Användarkonton**: Skapas på formen `fornamn.efternamn@domän.se` (duplicerade namn får suffix .1-.5)
- **Säkerhetsgrupp**: Endast konton i gruppen "Scoutnet" påverkas av synkroniseringen
- **CustomAttribute1**: Innehåller Scoutnet ID för automatisk grupphantering
- **Aktivering/inaktivering**: Konton som inte längre matchar inaktiveras automatiskt
- **Distribution lists**: Synkroniseras med Scoutnet e-postlistor (kräver Distribution lists, inte Office365 grupper)

**Konfiguration:**
- **Azure Automation runbook** med funktionen `Invoke-SNSUppdateOffice365User`
- **Schemalagd körning** (rekommenderas nattetid kl 3-4)
- **Flexibla synkroniseringsalternativ** för ledare vs scouter (Office365-konton, Scoutnet-adresser eller båda)
- **Statiska listor** stöds för avancerad regelhantering

Se repositoryt för detaljerade instruktioner om konfiguration av Azure Automation, distributionsgrupper och Scoutnet-listor.

### Standard OIDC

För generiska OIDC-klienter:

1. **Client Protocol**: `openid-connect`
2. **Access Type**: `confidential` (för server-side apps) eller `public` (för SPA)
3. **Standard Flow Enabled**: På
4. **Valid Redirect URIs**: Lägg till applikationens callback-URL

**Viktiga endpoints:**
- Authorization: `/realms/scoutnet/protocol/openid-connect/auth`
- Token: `/realms/scoutnet/protocol/openid-connect/token`
- UserInfo: `/realms/scoutnet/protocol/openid-connect/userinfo`

### Joomla

**Plugin**: Använd [miniOrange OpenID Connect SSO](https://plugins.miniorange.com/joomla-single-sign-on-with-custom-openid-connect-provider) plugin

1. **Skapa klient** med `Access Type: confidential`
2. **Valid Redirect URIs**: `https://[din-joomla-site]/index.php?option=com_miniorange_oauth&task=callback`

**Plugin-konfiguration:**
- **Client ID och Secret**: Från Keycloak-klienten
- **Authorization Endpoint**: `https://dev.id.scouterna.se/realms/scoutnet/protocol/openid-connect/auth`
- **Access Token Endpoint**: `https://dev.id.scouterna.se/realms/scoutnet/protocol/openid-connect/token`
- **Get User Info Endpoint**: `https://dev.id.scouterna.se/realms/scoutnet/protocol/openid-connect/userinfo`

### Photoprism

1. **Client Protocol**: `openid-connect`
2. **Access Type**: `confidential`
3. **Valid Redirect URIs**: `https://[photoprism-url]/api/v1/oauth/[provider]/callback`

**Photoprism settings.yml:**
```yaml
OIDC_URI: "https://dev.id.scouterna.se/realms/scoutnet"
OIDC_CLIENT: "[client-id]"
OIDC_SECRET: "[client-secret]"
```

## Medlemsnummer som sub

För klienter som behöver känna till användarens `sub` (subject identifier) i förväg kan du konfigurera att använda Scoutnet-medlemsnumret istället för Keycloaks automatgenererade UUID.

**Varning**: Detta kan orsaka problem om du senare vill använda kombinerade inloggningsmetoder.

### Konfiguration

1. **Skapa klienten** som vanligt
2. Gå till **Client Scopes** → `[client-name]-dedicated`
3. **Add mapper** → **By configuration** → **User Attribute**
4. Konfigurera mappern:
   - **Mapper type**: User Attribute
   - **Name**: sub member_no mapper
   - **User Attribute**: `scoutnet_member_no`
   - **Token Claim Name**: `sub`
   - **Claim JSON Type**: String
   - **Add to ID token**: På
   - **Add to access token**: På
   - **Add to lightweight access token**: På
   - **Add to userinfo**: På
   - **Add to token introspection**: På
   - **Multivalued**: Av
   - **Aggregate attribute values**: Av

5. **Spara** konfigurationen

Nu kommer klienten att få medlemsnumret som `sub` istället för UUID.

## Tillåt inloggning med Microsoft som provider

Microsoft är redan konfigurerad som en identity provider i ScoutID Keycloak, men är dold som standard. För att aktivera Microsoft-inloggning för en specifik klient:


### Manuell parameter

Lägg till parametern `kc_idp_hint=microsoft-scouterna-dev` till inloggnings-URL:en:

```
https://dev.id.scouterna.se/realms/scoutnet/protocol/openid-connect/auth?
  client_id=[din-klient]&
  redirect_uri=[callback-url]&
  response_type=code&
  scope=openid&
  kc_idp_hint=microsoft-scouterna-dev
```


## Lokal utvecklingsmiljö

För att sätta upp en egen lokal testmiljö för att testa din klient mot, se instruktionerna i [scoutid-keycloak-provider repository](https://github.com/Scouterna/scoutid-keycloak-provider).

**Lokal testmiljö:**
1. Klona repositoryt och följ README-instruktionerna
2. Starta med `docker compose up`
3. **Admin-konsol**: https://localhost:8080/admin/
4. **Test-inloggning**: https://localhost:8080/realms/master/account/
5. **Inloggning**: admin/admin (för admin-konsolen)

### Testkonfiguration

För att testa din klientkonfiguration:

1. **Skapa en testklient** i admin-gränssnittet
2. **Konfigurera endpoints** enligt instruktionerna för din applikationstyp
3. **Testa inloggning** med ditt Scoutnet-konto (personnummer + lösenord)
4. **Verifiera claims** genom att kontrollera vilken användarinformation som returneras

För detaljerade instruktioner om konfiguration av autentiseringsflöden och användarprofiler, se README-filen i provider-repositoryt.
