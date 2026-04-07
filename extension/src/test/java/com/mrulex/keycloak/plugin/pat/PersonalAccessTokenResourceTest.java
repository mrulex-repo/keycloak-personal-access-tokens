package com.mrulex.keycloak.plugin.pat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersonalAccessTokenResourceTest {

    static final String REALM = "test";
    static final String TEST_CLIENT = "test-client";
    static final String USER_ALICE = "alice";
    static final String USER_ALICE_PASSWORD = "alicepass";
    static final String USER_BRUTE = "bruteuser";
    static final String USER_BRUTE_PASSWORD = "brutepass";

    static KeycloakContainer keycloak;
    static HttpClient http;
    static ObjectMapper mapper;
    static String baseUrl;

    // Shared state between ordered tests
    static String aliceToken;        // Bearer token for alice
    static String createdPatId;      // ID of PAT created in create test
    static String validPatToken;     // plaintext token created for /auth tests

    @BeforeAll
    static void startKeycloak() throws Exception {
        mapper = new ObjectMapper();
        http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        File shadowJar = new File("build/libs/keycloak-personal-access-tokens-1.0.0.jar");
        assertThat(shadowJar).exists();

        keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.3.4")
                .withCopyToContainer(
                        MountableFile.forHostPath(shadowJar.getAbsolutePath()),
                        "/opt/keycloak/providers/keycloak-personal-access-tokens.jar")
                .withRealmImportFile("/test-realm.json");
        keycloak.start();

        baseUrl = keycloak.getAuthServerUrl();
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        // Realm, roles, client, and users are all created via the realm import file at startup.
        // Just obtain Alice's Bearer token here.
        aliceToken = getPasswordGrantToken(REALM, TEST_CLIENT, USER_ALICE, USER_ALICE_PASSWORD);
    }

    @AfterAll
    static void stopKeycloak() {
        if (keycloak != null) keycloak.stop();
    }

    // -------------------------------------------------------------------------
    // Tests: GET /personal-access-token (list)
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    void list_emptyForNewUser() throws Exception {
        HttpResponse<String> resp = get(aliceToken, patUrl());
        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(resp.body());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(0);
    }

    @Test
    @Order(2)
    void list_requiresAuth() throws Exception {
        HttpResponse<String> resp = get(null, patUrl());
        assertThat(resp.statusCode()).isEqualTo(401);
    }

    // -------------------------------------------------------------------------
    // Tests: POST /personal-access-token (create)
    // -------------------------------------------------------------------------

    @Test
    @Order(10)
    void create_returnsTokenOnce() throws Exception {
        String future = Instant.now().plus(365, ChronoUnit.DAYS).toString();
        String body = mapper.writeValueAsString(Map.of(
                "name", "maven-ci",
                "roles", List.of("maven-read", "maven-deploy"),
                "expires", future));
        HttpResponse<String> resp = post(aliceToken, patUrl(), body);
        assertThat(resp.statusCode()).isEqualTo(201);

        JsonNode json = mapper.readTree(resp.body());
        assertThat(json.get("id").asText()).isNotBlank();
        assertThat(json.get("name").asText()).isEqualTo("maven-ci");
        assertThat(json.get("token").asText()).hasSize(64);
        assertThat(json.get("roles").isArray()).isTrue();

        createdPatId = json.get("id").asText();
        validPatToken = json.get("token").asText();
    }

    @Test
    @Order(11)
    void create_appearsInList() throws Exception {
        HttpResponse<String> resp = get(aliceToken, patUrl());
        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode arr = mapper.readTree(resp.body());
        assertThat(arr.size()).isEqualTo(1);
        assertThat(arr.get(0).get("name").asText()).isEqualTo("maven-ci");
    }

    @Test
    @Order(12)
    void create_failsWithNoRoles() throws Exception {
        String body = mapper.writeValueAsString(Map.of("name", "no-roles-token", "roles", List.of()));
        HttpResponse<String> resp = post(aliceToken, patUrl(), body);
        assertThat(resp.statusCode()).isEqualTo(400);
    }

    @Test
    @Order(13)
    void create_failsWithDuplicateName() throws Exception {
        String body = mapper.writeValueAsString(Map.of("name", "maven-ci", "roles", List.of("maven-read")));
        HttpResponse<String> resp = post(aliceToken, patUrl(), body);
        assertThat(resp.statusCode()).isEqualTo(400);
    }

    @Test
    @Order(14)
    void create_failsWithPastExpiry() throws Exception {
        String past = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        String body = mapper.writeValueAsString(Map.of(
                "name", "expired-at-create",
                "roles", List.of("maven-read"),
                "expires", past));
        HttpResponse<String> resp = post(aliceToken, patUrl(), body);
        assertThat(resp.statusCode()).isEqualTo(400);
    }

    // -------------------------------------------------------------------------
    // Tests: GET /personal-access-token/roles
    // -------------------------------------------------------------------------

    @Test
    @Order(20)
    void listRoles_returnsRealmRoles() throws Exception {
        HttpResponse<String> resp = get(aliceToken, patUrl() + "/roles");
        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode arr = mapper.readTree(resp.body());
        assertThat(arr.isArray()).isTrue();
        boolean hasMavenRead = false;
        for (JsonNode role : arr) {
            if ("maven-read".equals(role.get("name").asText())) hasMavenRead = true;
        }
        assertThat(hasMavenRead).isTrue();
    }

    // -------------------------------------------------------------------------
    // Tests: GET /personal-access-token/auth
    // -------------------------------------------------------------------------

    @Test
    @Order(30)
    void auth_returns200WithHeadersForValidToken() throws Exception {
        String basic = basicAuth(USER_ALICE, validPatToken);
        HttpResponse<String> resp = authGet(basic, null);

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.headers().firstValue("X-User").orElse("")).isEqualTo(USER_ALICE);
        assertThat(resp.headers().firstValue("X-User-Id").orElse("")).isNotBlank();
        assertThat(resp.headers().firstValue("X-Roles").orElse("")).contains("maven-read");
    }

    @Test
    @Order(31)
    void auth_returns401ForWrongToken() throws Exception {
        String basic = basicAuth(USER_ALICE, "thisisthewrongtoken00000000000000000000000000000000000000000001");
        HttpResponse<String> resp = authGet(basic, null);
        assertThat(resp.statusCode()).isEqualTo(401);
    }

    @Test
    @Order(32)
    void auth_returns401ForUnknownUser() throws Exception {
        String basic = basicAuth("nobody", validPatToken);
        HttpResponse<String> resp = authGet(basic, null);
        assertThat(resp.statusCode()).isEqualTo(401);
    }

    @Test
    @Order(33)
    void auth_returns200WhenRequiredRoleIsPresent() throws Exception {
        String basic = basicAuth(USER_ALICE, validPatToken);
        HttpResponse<String> resp = authGet(basic, "maven-read");
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    @Test
    @Order(34)
    void auth_returns403WhenRequiredRoleMissing() throws Exception {
        String basic = basicAuth(USER_ALICE, validPatToken);
        HttpResponse<String> resp = authGet(basic, "docker-pull");
        assertThat(resp.statusCode()).isEqualTo(403);
    }

    @Test
    @Order(35)
    void auth_returns401ForExpiredToken() throws Exception {
        // Create a PAT that expires in 2 seconds, then wait for it to expire
        String soonExpires = Instant.now().plus(2, ChronoUnit.SECONDS).toString();
        String body = mapper.writeValueAsString(Map.of(
                "name", "expiring-soon",
                "roles", List.of("maven-read"),
                "expires", soonExpires));
        HttpResponse<String> createResp = post(aliceToken, patUrl(), body);
        assertThat(createResp.statusCode()).isEqualTo(201);

        String expiredToken = mapper.readTree(createResp.body()).get("token").asText();
        Thread.sleep(3000);

        String basic = basicAuth(USER_ALICE, expiredToken);
        HttpResponse<String> authResp = authGet(basic, null);
        assertThat(authResp.statusCode()).isEqualTo(401);
    }

    @Test
    @Order(36)
    void auth_returns429AfterBruteForce() throws Exception {
        // Create a PAT for bruteuser
        String bruteBearer = getPasswordGrantToken(REALM, TEST_CLIENT, USER_BRUTE, USER_BRUTE_PASSWORD);
        String body = mapper.writeValueAsString(Map.of("name", "brute-token", "roles", List.of("maven-read")));
        HttpResponse<String> createResp = post(bruteBearer, patUrl(), body);
        assertThat(createResp.statusCode()).isEqualTo(201);

        // Make 3 failed attempts (wrong token) to trigger lockout
        String wrong = basicAuth(USER_BRUTE, "wrongtoken0000000000000000000000000000000000000000000000000001");
        for (int i = 0; i < 3; i++) {
            authGet(wrong, null);
        }

        // 4th attempt should be 429
        HttpResponse<String> resp = authGet(wrong, null);
        assertThat(resp.statusCode()).isEqualTo(429);
    }

    // -------------------------------------------------------------------------
    // Tests: DELETE /personal-access-token
    // -------------------------------------------------------------------------

    @Test
    @Order(40)
    void delete_removesCredential() throws Exception {
        String body = mapper.writeValueAsString(Map.of("id", createdPatId));
        HttpResponse<String> resp = delete(aliceToken, patUrl(), body);
        assertThat(resp.statusCode()).isEqualTo(204);

        // Verify it's gone
        HttpResponse<String> listResp = get(aliceToken, patUrl());
        JsonNode arr = mapper.readTree(listResp.body());
        boolean stillPresent = false;
        for (JsonNode item : arr) {
            if (createdPatId.equals(item.get("id").asText())) stillPresent = true;
        }
        assertThat(stillPresent).isFalse();
    }

    @Test
    @Order(41)
    void delete_returns404ForUnknownId() throws Exception {
        String body = mapper.writeValueAsString(Map.of("id", "00000000-0000-0000-0000-000000000000"));
        HttpResponse<String> resp = delete(aliceToken, patUrl(), body);
        assertThat(resp.statusCode()).isEqualTo(404);
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    static String patUrl() {
        return baseUrl + "realms/" + REALM + "/personal-access-token";
    }

    static HttpResponse<String> get(String bearerToken, String url) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .GET();
        if (bearerToken != null) req.header("Authorization", "Bearer " + bearerToken);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(String bearerToken, String url, String jsonBody) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (bearerToken != null) req.header("Authorization", "Bearer " + bearerToken);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> delete(String bearerToken, String url, String jsonBody) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody));
        if (bearerToken != null) req.header("Authorization", "Bearer " + bearerToken);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> authGet(String basicCredential, String requiredRole) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(patUrl() + "/auth"))
                .GET()
                .header("Authorization", basicCredential);
        if (requiredRole != null) req.header("X-Required-Role", requiredRole);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String basicAuth(String username, String token) {
        String raw = username + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static String getPasswordGrantToken(String realm, String clientId, String username, String password)
            throws Exception {
        String body = "grant_type=password&client_id=" + clientId
                + "&username=" + username
                + "&password=" + password;
        HttpRequest req = HttpRequest.newBuilder(
                URI.create(baseUrl + "realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode json = mapper.readTree(resp.body());
        JsonNode token = json.get("access_token");
        if (token == null) {
            throw new IllegalStateException(
                    "Failed to get token for user '" + username + "': " + resp.body());
        }
        return token.asText();
    }
}
