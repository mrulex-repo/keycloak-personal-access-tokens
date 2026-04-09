package com.mrulex.keycloak.plugin.pat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrulex.keycloak.plugin.pat.dto.PatCreateRequestDto;
import com.mrulex.keycloak.plugin.pat.dto.PatCredentialData;
import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.keycloak.common.ClientConnection;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.BruteForceProtector;

public final class PatUtils {

  public static final String CREDENTIAL_TYPE = "personal-access-token";

  private static final Pattern TOKEN_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]{0,63}$");
  private static final int TOKEN_BYTES = 48; // 48 bytes → 64 Base64url chars
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Argon2ID parameters: 64 MiB memory, 5 iterations, 1 parallelism, 64-byte output
  private static final int ARGON2_MEMORY = 65536;
  private static final int ARGON2_ITER = 5;
  private static final int ARGON2_PARALLEL = 1;
  private static final int ARGON2_LENGTH = 64;

  private PatUtils() {}

  // -------------------------------------------------------------------------
  // Token generation
  // -------------------------------------------------------------------------

  public static String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  // -------------------------------------------------------------------------
  // Hashing
  // -------------------------------------------------------------------------

  public static Argon2Function hashFunction() {
    return Argon2Function.getInstance(
        ARGON2_MEMORY, ARGON2_ITER, ARGON2_PARALLEL, ARGON2_LENGTH, Argon2.ID);
  }

  public static String hashToken(String plaintext) {
    return Password.hash(plaintext).with(hashFunction()).getResult();
  }

  public static boolean verifyToken(String plaintext, String hash) {
    return Password.check(plaintext, hash).with(hashFunction());
  }

  // -------------------------------------------------------------------------
  // CredentialModel helpers
  // -------------------------------------------------------------------------

  public static CredentialModel buildCredential(PatCreateRequestDto request, String hash) {
    try {
      Map<String, Object> secretData = new HashMap<>();
      secretData.put("hash", hash);

      Map<String, Object> credentialData = new HashMap<>();
      credentialData.put("roles", request.roles());
      if (request.expires() != null && !request.expires().isBlank()) {
        credentialData.put("expires", request.expires());
      }

      CredentialModel model = new CredentialModel();
      model.setType(CREDENTIAL_TYPE);
      model.setUserLabel(request.name());
      model.setCreatedDate(System.currentTimeMillis());
      model.setSecretData(MAPPER.writeValueAsString(secretData));
      model.setCredentialData(MAPPER.writeValueAsString(credentialData));
      return model;
    } catch (Exception e) {
      throw new RuntimeException("Failed to build credential model", e);
    }
  }

  public static String extractHash(CredentialModel cred) {
    try {
      Map<?, ?> data = MAPPER.readValue(cred.getSecretData(), Map.class);
      return (String) data.get("hash");
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse secretData for credential " + cred.getId(), e);
    }
  }

  public static PatCredentialData extractCredentialData(CredentialModel cred) {
    try {
      Map<?, ?> data = MAPPER.readValue(cred.getCredentialData(), Map.class);
      @SuppressWarnings("unchecked")
      List<String> roles = (List<String>) data.get("roles");
      String expires = (String) data.get("expires");
      return new PatCredentialData(roles != null ? roles : List.of(), expires);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to parse credentialData for credential " + cred.getId(), e);
    }
  }

  // -------------------------------------------------------------------------
  // Credential queries
  // -------------------------------------------------------------------------

  public static Stream<CredentialModel> streamPats(UserModel user) {
    return user.credentialManager().getStoredCredentialsByTypeStream(CREDENTIAL_TYPE);
  }

  // -------------------------------------------------------------------------
  // Validation
  // -------------------------------------------------------------------------

  public static void validateTokenName(String name, UserModel user) {
    if (name == null || !TOKEN_NAME_PATTERN.matcher(name).matches()) {
      throw badRequest(
          "name must be 1–64 characters, start with a letter, and contain only [a-z0-9_-]");
    }
    boolean duplicate = streamPats(user).anyMatch(c -> name.equals(c.getUserLabel()));
    if (duplicate) {
      throw badRequest("a token named '" + name + "' already exists");
    }
  }

  public static void validateExpires(String expires) {
    if (expires == null || expires.isBlank()) {
      return; // optional
    }
    try {
      Instant expiry = Instant.parse(expires);
      if (!expiry.isAfter(Instant.now())) {
        throw badRequest("expires must be a future datetime");
      }
    } catch (DateTimeParseException e) {
      throw badRequest("expires must be a valid ISO-8601 UTC datetime (e.g. 2027-04-06T10:00:00Z)");
    }
  }

  public static void validateRoles(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      throw badRequest("roles must be a non-empty list");
    }
  }

  public static boolean isExpired(String expires) {
    if (expires == null || expires.isBlank()) {
      return false;
    }
    try {
      return !Instant.parse(expires).isAfter(Instant.now());
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  // -------------------------------------------------------------------------
  // Brute-force protection
  // -------------------------------------------------------------------------

  public static void checkBruteForce(KeycloakSession session, RealmModel realm, UserModel user) {
    BruteForceProtector protector = session.getProvider(BruteForceProtector.class);
    if (protector == null) return;
    if (protector.isTemporarilyDisabled(session, realm, user)) {
      throw new WebApplicationException(
          Response.status(429)
              .entity("Too many failed attempts — account temporarily locked")
              .type(MediaType.TEXT_PLAIN_TYPE)
              .build());
    }
  }

  public static void recordFailedAttempt(
      KeycloakSession session, RealmModel realm, UserModel user) {
    BruteForceProtector protector = session.getProvider(BruteForceProtector.class);
    if (protector == null) return;
    ClientConnection connection = session.getContext().getConnection();
    protector.failedLogin(realm, user, connection, session.getContext().getUri());
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private static WebApplicationException badRequest(String message) {
    return new WebApplicationException(
        Response.status(Response.Status.BAD_REQUEST)
            .entity(message)
            .type(MediaType.TEXT_PLAIN_TYPE)
            .build());
  }
}
