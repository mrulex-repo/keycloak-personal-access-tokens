package com.mrulex.keycloak.plugin.pat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jacoco.core.tools.ExecDumpClient;
import org.jacoco.core.tools.ExecFileLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.utility.MountableFile;

/**
 * Integration tests for {@link PatBasicAuthAuthenticator} and {@link PatRolesTokenMapper}.
 *
 * <p>Tests issue tokens via the OIDC Direct Grant (ROPC) flow using a PAT as the password, against
 * a real Keycloak 26 container. The {@code pat-test-client} in the test realm is configured to use
 * the {@code pat-direct-grant} flow (backed by {@code PatBasicAuthAuthenticator}) and has the
 * {@code PatRolesTokenMapper} registered to filter JWT roles.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PatBasicAuthAuthenticatorTest {

  static final String REALM = "test";
  static final String PAT_CLIENT = "pat-test-client";
  static final String TEST_CLIENT = "test-client";
  static final String USER_ALICE = "alice";
  static final String ALICE_PASS = "alicepass";
  static final String USER_CAROL = "carol";
  static final String CAROL_PASS = "carolpass";

  static final int JACOCO_PORT = 6300;

  static KeycloakContainer keycloak;
  static HttpClient http;
  static ObjectMapper mapper;
  static String baseUrl;

  // Shared state between ordered tests
  static String aliceBearer; // Bearer token for Alice (normal login, test-client)
  static String validPatToken; // plaintext PAT used for ROPC tests
  static String mavenReadOnlyPatToken; // PAT with only maven-read — for role filtering test

  @BeforeAll
  static void startKeycloak() throws Exception {
    mapper = new ObjectMapper();
    http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    File shadowJar = new File("build/libs/keycloak-personal-access-tokens-1.0.0.jar");
    assertThat(shadowJar).exists();

    KeycloakContainer container =
        new KeycloakContainer("quay.io/keycloak/keycloak:26.3.4")
            .withCopyToContainer(
                MountableFile.forHostPath(shadowJar.getAbsolutePath()),
                "/opt/keycloak/providers/keycloak-personal-access-tokens.jar")
            .withRealmImportFile("/test-realm.json");

    String agentJar = System.getProperty("jacocoAgentJar");
    if (agentJar != null) {
      container
          .withCopyToContainer(MountableFile.forHostPath(agentJar), "/jacoco/jacocoagent.jar")
          .withEnv(
              "JAVA_OPTS_APPEND",
              "-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,port="
                  + JACOCO_PORT
                  + ",address=*")
          .addExposedPort(JACOCO_PORT);
    }

    keycloak = container;
    keycloak.start();

    baseUrl = keycloak.getAuthServerUrl();
    if (!baseUrl.endsWith("/")) baseUrl += "/";

    // Alice's Bearer token via normal password grant (test-client uses default flow)
    aliceBearer = getPasswordGrantToken(TEST_CLIENT, USER_ALICE, ALICE_PASS);
  }

  @AfterAll
  static void stopKeycloak() throws Exception {
    if (keycloak != null) {
      dumpJacocoCoverage("it-PatBasicAuthAuthenticatorTest");
      keycloak.stop();
    }
  }

  static void dumpJacocoCoverage(String name) {
    if (System.getProperty("jacocoAgentJar") == null) return;
    try {
      int port = keycloak.getMappedPort(JACOCO_PORT);
      ExecFileLoader loader = new ExecDumpClient().dump("localhost", port);
      Path dest = Path.of("build/jacoco/" + name + ".exec");
      Files.createDirectories(dest.getParent());
      loader.save(dest.toFile(), true);
    } catch (Exception e) {
      System.err.println("JaCoCo dump failed: " + e.getMessage());
    }
  }

  // -------------------------------------------------------------------------
  // Setup: create PATs for Alice before testing authentication
  // -------------------------------------------------------------------------

  @Test
  @Order(1)
  void setup_createPatForAlice() throws Exception {
    String future = Instant.now().plus(365, ChronoUnit.DAYS).toString();
    String body =
        mapper.writeValueAsString(
            Map.of(
                "name",
                "ropc-token",
                "roles",
                List.of("maven-read", "maven-deploy"),
                "expires",
                future));
    HttpResponse<String> resp = post(aliceBearer, patUrl(), body);
    assertThat(resp.statusCode()).isEqualTo(201);
    validPatToken = mapper.readTree(resp.body()).get("token").asText();
    assertThat(validPatToken).hasSize(64);
  }

  @Test
  @Order(2)
  void setup_createMavenReadOnlyPatForAlice() throws Exception {
    String future = Instant.now().plus(365, ChronoUnit.DAYS).toString();
    String body =
        mapper.writeValueAsString(
            Map.of("name", "maven-read-only", "roles", List.of("maven-read"), "expires", future));
    HttpResponse<String> resp = post(aliceBearer, patUrl(), body);
    assertThat(resp.statusCode()).isEqualTo(201);
    mavenReadOnlyPatToken = mapper.readTree(resp.body()).get("token").asText();
    assertThat(mavenReadOnlyPatToken).hasSize(64);
  }

  // -------------------------------------------------------------------------
  // Tests: valid PAT via ROPC → JWT issued
  // -------------------------------------------------------------------------

  @Test
  @Order(10)
  void validPat_issuesJwt() throws Exception {
    HttpResponse<String> resp = patDirectGrant(USER_ALICE, validPatToken);
    assertThat(resp.statusCode()).isEqualTo(200);

    JsonNode json = mapper.readTree(resp.body());
    assertThat(json.has("access_token")).isTrue();
    assertThat(json.get("access_token").asText()).isNotBlank();
  }

  @Test
  @Order(11)
  void validPat_jwtContainsPatRoles() throws Exception {
    HttpResponse<String> resp = patDirectGrant(USER_ALICE, validPatToken);
    assertThat(resp.statusCode()).isEqualTo(200);

    Set<String> roles =
        decodeJwtRealmRoles(mapper.readTree(resp.body()).get("access_token").asText());
    assertThat(roles).containsExactlyInAnyOrder("maven-read", "maven-deploy");
  }

  // -------------------------------------------------------------------------
  // Tests: PAT role filtering — only PAT roles appear in JWT
  // -------------------------------------------------------------------------

  @Test
  @Order(12)
  void patRolesFiltered_jwtContainsOnlyPatRoles() throws Exception {
    // Alice has maven-read AND maven-deploy assigned as realm roles.
    // The PAT only grants maven-read.
    // The PatRolesTokenMapper must filter the JWT to only maven-read.
    HttpResponse<String> resp = patDirectGrant(USER_ALICE, mavenReadOnlyPatToken);
    assertThat(resp.statusCode()).isEqualTo(200);

    Set<String> roles =
        decodeJwtRealmRoles(mapper.readTree(resp.body()).get("access_token").asText());
    assertThat(roles).containsExactly("maven-read");
    assertThat(roles).doesNotContain("maven-deploy");
  }

  // -------------------------------------------------------------------------
  // Tests: invalid credentials return 401
  // -------------------------------------------------------------------------

  @Test
  @Order(20)
  void wrongToken_returns401() throws Exception {
    String wrongToken = "a".repeat(64);
    HttpResponse<String> resp = patDirectGrant(USER_ALICE, wrongToken);
    assertThat(resp.statusCode()).isEqualTo(401);
  }

  @Test
  @Order(21)
  void unknownUser_returns401() throws Exception {
    HttpResponse<String> resp = patDirectGrant("nobody", validPatToken);
    assertThat(resp.statusCode()).isEqualTo(401);
  }

  @Test
  @Order(22)
  void userWithNoPats_returns401() throws Exception {
    // Carol has no PATs — any token attempt should fail
    HttpResponse<String> resp = patDirectGrant(USER_CAROL, validPatToken);
    assertThat(resp.statusCode()).isEqualTo(401);
  }

  // -------------------------------------------------------------------------
  // Tests: expired PAT returns 401
  // -------------------------------------------------------------------------

  @Test
  @Order(30)
  void expiredPat_returns401() throws Exception {
    // Create a PAT expiring very soon
    String soonExpires = Instant.now().plus(2, ChronoUnit.SECONDS).toString();
    String body =
        mapper.writeValueAsString(
            Map.of(
                "name", "expiring-ropc", "roles", List.of("maven-read"), "expires", soonExpires));
    HttpResponse<String> createResp = post(aliceBearer, patUrl(), body);
    assertThat(createResp.statusCode()).isEqualTo(201);
    String expiredToken = mapper.readTree(createResp.body()).get("token").asText();

    Thread.sleep(3000);

    HttpResponse<String> resp = patDirectGrant(USER_ALICE, expiredToken);
    assertThat(resp.statusCode()).isEqualTo(401);
  }

  // -------------------------------------------------------------------------
  // HTTP helpers
  // -------------------------------------------------------------------------

  static String patUrl() {
    return baseUrl + "realms/" + REALM + "/personal-access-token";
  }

  static HttpResponse<String> post(String bearerToken, String url, String jsonBody)
      throws Exception {
    HttpRequest.Builder req =
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
    if (bearerToken != null) req.header("Authorization", "Bearer " + bearerToken);
    return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Issues a token via the OIDC Direct Grant (ROPC) flow using {@code pat-test-client}. This client
   * is bound to the {@code pat-direct-grant} flow, so {@code PatBasicAuthAuthenticator} handles the
   * authentication.
   */
  static HttpResponse<String> patDirectGrant(String username, String patToken) throws Exception {
    String body =
        "grant_type=password&client_id="
            + PAT_CLIENT
            + "&username="
            + username
            + "&password="
            + patToken;
    HttpRequest req =
        HttpRequest.newBuilder(
                URI.create(baseUrl + "realms/" + REALM + "/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }

  static String getPasswordGrantToken(String clientId, String username, String password)
      throws Exception {
    String body =
        "grant_type=password&client_id="
            + clientId
            + "&username="
            + username
            + "&password="
            + password;
    HttpRequest req =
        HttpRequest.newBuilder(
                URI.create(baseUrl + "realms/" + REALM + "/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    JsonNode json = mapper.readTree(resp.body());
    JsonNode token = json.get("access_token");
    if (token == null) {
      throw new IllegalStateException("Failed to get token for '" + username + "': " + resp.body());
    }
    return token.asText();
  }

  /** Base64url-decodes the JWT payload and extracts {@code realm_access.roles}. */
  static Set<String> decodeJwtRealmRoles(String jwt) throws Exception {
    String[] parts = jwt.split("\\.");
    // Add padding so standard decoder works
    String padded = parts[1];
    switch (padded.length() % 4) {
      case 2 -> padded += "==";
      case 3 -> padded += "=";
    }
    String payload = new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
    JsonNode root = mapper.readTree(payload);
    JsonNode roles = root.path("realm_access").path("roles");
    return StreamSupport.stream(roles.spliterator(), false)
        .map(JsonNode::asText)
        .collect(Collectors.toSet());
  }
}
