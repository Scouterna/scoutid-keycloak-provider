# E2E Integration Tests — Future Plan

## Goal

Test the full authentication flow (login, cookie re-auth, remember-me, session expiry) against a real Keycloak instance with a mock Scoutnet API. No real Scoutnet credentials needed.

## Architecture

```
Test JVM
├── Mock Scoutnet HTTP server (com.sun.net.httpserver.HttpServer)
│   ├── POST /api/authenticate → returns mock token
│   ├── GET /api/get/profile → returns mock profile JSON
│   ├── GET /api/get/user_roles → returns mock roles JSON
│   └── POST /api/refresh_token → returns refreshed token
│
├── Testcontainers Keycloak (quay.io/keycloak/keycloak:26.5.x)
│   ├── Provider JAR loaded via withProviderClassesFrom("target/classes")
│   ├── SCOUTNET_BASE_URL=http://host.testcontainers.internal:{mockPort}
│   └── Auth flow configured via admin REST API
│
└── HTTP client → drives login/cookie flows against Keycloak
```

## Test scenarios

1. **Login with password succeeds** — submit credentials, verify redirect to callback
2. **Login with wrong password fails** — submit bad credentials, verify login form shown again
3. **Cookie re-auth works with remember-me** — login with remember-me, second request uses cookie auth
4. **No remember-me → session expires** — login without remember-me, wait for idle timeout, verify login form
5. **Remember-me survives normal timeout** — login with remember-me, wait past normal idle but within remember-me idle
6. **Remember-me eventually expires** — login with remember-me, wait past remember-me idle timeout

## Session timeouts for testing

Use very short timeouts so tests complete quickly:
```json
{
    "ssoSessionIdleTimeout": 3,
    "ssoSessionMaxLifespan": 6,
    "ssoSessionIdleTimeoutRememberMe": 10,
    "ssoSessionMaxLifespanRememberMe": 15
}
```

## Keycloak flow setup via admin API

```
POST /admin/realms                                    → create realm with rememberMe=true
POST /admin/realms/{realm}/clients                    → create public test client
POST /admin/realms/{realm}/authentication/flows       → create "scoutid-browser" flow
POST .../flows/scoutid-browser/executions/execution   → add scoutnet-cookie-authenticator
POST .../flows/scoutid-browser/executions/execution   → add scoutnet-authenticator
GET  .../flows/scoutid-browser/executions             → get execution IDs
PUT  .../flows/scoutid-browser/executions             → set both to ALTERNATIVE
PUT  /admin/realms/{realm}                            → bind flow as browserFlow
```

## Known blocker: Java CookieManager + localhost

Java's built-in `CookieManager` maps `localhost` to `localhost.local` internally, then refuses to send cookies back because the domains don't match. This is a long-standing JDK bug that breaks cookie-based testing against localhost.

### Solutions (pick one when implementing)

1. **Use OkHttp instead of java.net.http.HttpClient** — OkHttp handles localhost cookies correctly:
   ```xml
   <dependency>
       <groupId>com.squareup.okhttp3</groupId>
       <artifactId>okhttp</artifactId>
       <version>4.12.0</version>
       <scope>test</scope>
   </dependency>
   ```
   OkHttp's `CookieJar` with an in-memory store doesn't have the localhost domain issue.

2. **Use Apache HttpClient 5** — also handles localhost cookies correctly:
   ```xml
   <dependency>
       <groupId>org.apache.httpcomponents.client5</groupId>
       <artifactId>httpclient5</artifactId>
       <version>5.4</version>
       <scope>test</scope>
   </dependency>
   ```

3. **Custom CookieHandler** — implement a `java.net.CookieHandler` subclass that stores/returns all cookies without domain validation. Most work, least dependencies.

## Dependencies needed

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.github.dasniko</groupId>
    <artifactId>testcontainers-keycloak</artifactId>
    <version>3.6.0</version>
    <scope>test</scope>
</dependency>
<!-- Plus one of the HTTP client options above -->
```

## Notes

- Use `Testcontainers.exposeHostPorts(mockPort)` to make the mock Scoutnet reachable from inside the container
- The testcontainers-keycloak library already runs Keycloak in dev mode — don't use `withCommand()`
- Test class should be named `*IT.java` so failsafe picks it up (not surefire)
- Docker must be running on the machine executing the tests
