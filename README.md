# Keycloak Personal Access Tokens

A Keycloak plugin that lets users create **personal access tokens (PATs)** — scoped, named tokens that can be used as passwords anywhere HTTP Basic Auth is accepted.

Built for Keycloak 26.

---

## The problem

Many services — Maven registries, Docker registries, npm proxies, IMAP servers — only speak HTTP Basic Auth. You can't give them an OAuth flow, and you don't want to use your real password. You want:

- A token that works as a password for that specific service
- Scoped to exactly the roles that service needs
- Revokable without changing your password
- With an optional expiry date

Keycloak doesn't have this built in. This plugin adds it.

---

## Demo

<video src="docs/assets/showcase.webm" controls width="100%"></video>

---

## How it works

Users create PATs from the Keycloak Account UI. Each token carries a set of realm roles chosen at creation time. When a service authenticates a request, it sends `Authorization: Basic base64(username:token)` to the plugin's `/auth` endpoint. Keycloak validates the token and returns the user's PAT roles in the `X-Roles` response header.

The token is shown **once** on creation and never stored in plaintext — only an Argon2ID hash is kept.

### Auth endpoint response headers

| Header | Content |
|--------|---------|
| `X-User` | Keycloak username |
| `X-User-Id` | Keycloak user UUID |
| `X-Roles` | Comma-separated roles assigned to the token |

Pass `X-Required-Role: <role>` in the request to let Keycloak enforce the check and return `403` directly instead of leaving it to the caller.

---

## Installation

### Prerequisites

- Keycloak 26
- Java 21 runtime (provided by Keycloak)

### 1. Download the JARs

Grab both JARs from the [latest GitHub release](https://github.com/mrulex-repo/keycloak-personal-access-tokens/releases/latest):

- `keycloak-personal-access-tokens-<version>.jar` — the SPI extension
- `keycloak-personal-access-tokens-theme-<version>.jar` — the Account UI theme

### 2. Drop them into Keycloak

```bash
cp keycloak-personal-access-tokens-*.jar       /opt/keycloak/providers/
cp keycloak-personal-access-tokens-theme-*.jar /opt/keycloak/providers/
```

Restart Keycloak. The plugin auto-registers via the standard SPI discovery mechanism.

### 3. Configure the authentication flow (for the Basic Auth Authenticator)

If you want clients to authenticate using PATs via the Direct Grant flow (e.g. `grant_type=password`):

1. In Keycloak Admin → **Authentication** → create a new flow based on **Direct Grant**
2. Replace the default username/password step with **PAT Basic Auth Authenticator**
3. Add the **PAT Roles Token Mapper** to the client's dedicated scope or to the flow's client
4. In the client settings → **Advanced** → **Authentication Flow Overrides** → set **Direct Grant Flow** to your new flow

> Other clients (Admin Console, Account UI) continue using the default flow and require the real password.

---

## Usage

### curl

```bash
# Authenticate and read roles
curl -u alice:my-pat-token \
  https://keycloak.example.com/realms/myrealm/personal-access-token/auth

# Enforce a required role server-side
curl -u alice:my-pat-token \
  -H "X-Required-Role: maven-read" \
  https://keycloak.example.com/realms/myrealm/personal-access-token/auth
# → 200 allowed  |  401 invalid/expired  |  403 role missing
```

### nginx — protecting a Maven repository

```nginx
location /maven/ {
    auth_request        /_pat_auth_maven_read;
    auth_request_set    $pat_user    $upstream_http_x_user;
    auth_request_set    $pat_user_id $upstream_http_x_user_id;

    proxy_set_header    Authorization  "";   # strip PAT from upstream
    proxy_set_header    X-User         $pat_user;
    proxy_set_header    X-User-Id      $pat_user_id;

    proxy_pass          http://nexus:8081/maven/;
}

location = /_pat_auth_maven_read {
    internal;
    proxy_pass              https://keycloak.example.com/realms/myrealm/personal-access-token/auth;
    proxy_pass_request_body off;
    proxy_set_header        Content-Length   "";
    proxy_set_header        X-Required-Role  "maven-read";
}
```

### Traefik forwardAuth

```yaml
http:
  middlewares:
    pat-maven-read:
      forwardAuth:
        address: "https://keycloak.example.com/realms/myrealm/personal-access-token/auth"
        authRequestHeaders:
          - "Authorization"
        authResponseHeaders:
          - "X-User"
          - "X-User-Id"
        headers:
          customRequestHeaders:
            X-Required-Role: "maven-read"

  routers:
    maven:
      rule: "PathPrefix(`/maven`)"
      middlewares: [pat-maven-read]
      service: nexus
```

### Spring Boot

Register a custom `AuthenticationProvider` that calls the `/auth` endpoint and maps `X-Roles` into Spring Security's `GrantedAuthority` list:

```java
@Component
public class PatAuthenticationProvider implements AuthenticationProvider {

    private final RestClient keycloak; // configured with Keycloak base URL

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        String username = auth.getName();
        String token = auth.getCredentials().toString();
        String basic = Base64.getEncoder()
            .encodeToString((username + ":" + token).getBytes());

        ResponseEntity<Void> response = keycloak.get()
            .uri("/realms/{realm}/personal-access-token/auth", "myrealm")
            .header("Authorization", "Basic " + basic)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                throw new BadCredentialsException("Invalid or expired token");
            })
            .toBodilessEntity();

        List<GrantedAuthority> authorities = Arrays
            .stream(response.getHeaders().getFirst("X-Roles").split(","))
            .map(r -> new SimpleGrantedAuthority(
                "ROLE_" + r.trim().toUpperCase().replace("-", "_")))
            .toList();

        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    @Override
    public boolean supports(Class<?> auth) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(auth);
    }
}
```

Then use `@PreAuthorize` for role enforcement:

```java
@GetMapping("/maven/**")
@PreAuthorize("hasRole('MAVEN_READ')")
public ResponseEntity<?> read() { ... }
```

---

## Development

### Prerequisites

- Java 21
- Docker (for integration tests and E2E)
- Node.js + pnpm (for the Account UI theme)
- [`just`](https://just.systems) task runner

### Common tasks

```bash
# Build everything (extension + theme)
just build

# Package both JARs into build/
just package

# Run E2E tests (builds, packages, starts Keycloak in Docker, runs Playwright)
just e2e

# Full CI pipeline
just ci
```

### Project structure

```
keycloak-personal-access-tokens/
├── extension/      ← Keycloak SPI extension (Java 21, Gradle)
├── theme/          ← Account UI theme (React/TypeScript, keycloakify)
├── e2e/            ← End-to-end tests (Playwright + Docker)
├── build/          ← Packaged JARs (git-ignored)
└── justfile        ← Build orchestration
```

### Releasing

```bash
# Compute the next semver from commit history, tag, and push
just release

# Upload artifacts to GitHub Releases (requires GITHUB_TOKEN)
just publish
```

The `release` recipe follows conventional commits: `feat:` → minor bump, `fix:` / other → patch bump, any `!` or `breaking:` → major bump.

---

## License

[GPL-3.0](LICENSE)
