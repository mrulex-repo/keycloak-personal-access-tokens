# Spring Boot Integration

Validate Personal Access Tokens in a Spring Boot application by calling the
Keycloak `/auth` endpoint from a custom `AuthenticationProvider`.

## PatAuthenticationProvider

```java
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.stream.Collectors;

public class PatAuthenticationProvider implements AuthenticationProvider {

    private final String patAuthUrl;
    private final RestTemplate restTemplate;

    public PatAuthenticationProvider(String keycloakUrl, String realm) {
        this.patAuthUrl = keycloakUrl + "/realms/" + realm + "/personal-access-token/auth";
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        UsernamePasswordAuthenticationToken token =
                (UsernamePasswordAuthenticationToken) authentication;
        String username = token.getName();
        String pat = (String) token.getCredentials();

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, pat);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Void> response;
        try {
            response = restTemplate.exchange(patAuthUrl, HttpMethod.GET, request, Void.class);
        } catch (Exception ex) {
            throw new BadCredentialsException("PAT authentication failed", ex);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BadCredentialsException("Invalid or expired token");
        }

        List<GrantedAuthority> authorities = parseRoles(response.getHeaders());
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private List<GrantedAuthority> parseRoles(HttpHeaders headers) {
        String rolesHeader = headers.getFirst("X-Roles");
        if (rolesHeader == null || rolesHeader.isBlank()) return List.of();
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                .collect(Collectors.toList());
    }
}
```

## Security Configuration

```java
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PatAuthenticationProvider patAuthenticationProvider() {
        return new PatAuthenticationProvider(
                "http://keycloak:8080",
                "myrealm"
        );
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           PatAuthenticationProvider patProvider) throws Exception {
        http
            .authenticationProvider(patProvider)
            .httpBasic(basic -> {})
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

## Requiring a Specific Role

To enforce a required role at the Keycloak layer (rather than Spring Security),
add the `X-Required-Role` header when calling the `/auth` endpoint:

```java
headers.set("X-Required-Role", "maven-deploy");
```

Keycloak returns `403` if the PAT does not carry that role, and the provider
will throw `BadCredentialsException`.

## Caching

Each request to `/auth` triggers an Argon2id hash verification in Keycloak,
which is intentionally slow. For high-throughput services, add a short-lived
cache (e.g. Caffeine with a 30-second TTL) keyed on the Base64-encoded
`Authorization` header value.
