package com.mrulex.keycloak.plugin.pat;

import com.mrulex.keycloak.plugin.pat.dto.PatCreateRequestDto;
import com.mrulex.keycloak.plugin.pat.dto.PatCreatedResponseDto;
import com.mrulex.keycloak.plugin.pat.dto.PatCredentialData;
import com.mrulex.keycloak.plugin.pat.dto.PatDeleteRequestDto;
import com.mrulex.keycloak.plugin.pat.dto.PatListItemDto;
import com.mrulex.keycloak.plugin.pat.dto.PatRoleDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

public class PersonalAccessTokenResource {

  private final KeycloakSession session;

  public PersonalAccessTokenResource(KeycloakSession session) {
    this.session = session;
  }

  // -------------------------------------------------------------------------
  // GET /personal-access-token
  // -------------------------------------------------------------------------

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response listPats() {
    UserModel user = requireAuthenticatedUser();
    List<PatListItemDto> items =
        PatUtils.streamPats(user)
            .map(
                cred -> {
                  PatCredentialData data = PatUtils.extractCredentialData(cred);
                  String created = Instant.ofEpochMilli(cred.getCreatedDate()).toString();
                  return new PatListItemDto(
                      cred.getId(), cred.getUserLabel(), created, data.expires(), data.roles());
                })
            .toList();
    return Response.ok(items).build();
  }

  // -------------------------------------------------------------------------
  // POST /personal-access-token
  // -------------------------------------------------------------------------

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createPat(PatCreateRequestDto request) {
    UserModel user = requireAuthenticatedUser();

    PatUtils.validateTokenName(request.name(), user);
    PatUtils.validateExpires(request.expires());
    PatUtils.validateRoles(request.roles(), user);

    String plaintext = PatUtils.generateToken();
    String hash = PatUtils.hashToken(plaintext);

    CredentialModel model = PatUtils.buildCredential(request, hash);
    CredentialModel stored = user.credentialManager().createStoredCredential(model);

    String created = Instant.ofEpochMilli(stored.getCreatedDate()).toString();
    PatCreatedResponseDto response =
        new PatCreatedResponseDto(
            stored.getId(),
            stored.getUserLabel(),
            plaintext,
            created,
            request.expires(),
            request.roles());

    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  // -------------------------------------------------------------------------
  // DELETE /personal-access-token
  // -------------------------------------------------------------------------

  @DELETE
  @Consumes(MediaType.APPLICATION_JSON)
  public Response deletePat(PatDeleteRequestDto request) {
    UserModel user = requireAuthenticatedUser();
    boolean removed = user.credentialManager().removeStoredCredentialById(request.id());
    if (!removed) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.noContent().build();
  }

  // -------------------------------------------------------------------------
  // GET /personal-access-token/roles
  // -------------------------------------------------------------------------

  @GET
  @Path("roles")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listRoles() {
    UserModel user = requireAuthenticatedUser();
    List<PatRoleDto> roles =
        user.getRealmRoleMappingsStream()
            .filter(role -> !role.getName().startsWith("default-roles-"))
            .map(role -> new PatRoleDto(role.getName(), role.getDescription()))
            .toList();
    return Response.ok(roles).build();
  }

  // -------------------------------------------------------------------------
  // GET /personal-access-token/auth
  // -------------------------------------------------------------------------

  @GET
  @Path("auth")
  public Response auth(
      @HeaderParam("Authorization") String authHeader,
      @HeaderParam("X-Required-Role") String requiredRole) {
    String[] decoded = parseBasicCredentials(authHeader);
    if (decoded == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    String username = decoded[0];
    String submittedToken = decoded[1];

    RealmModel realm = session.getContext().getRealm();
    UserModel user = session.users().getUserByUsername(realm, username);
    if (user == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    PatUtils.checkBruteForce(session, realm, user);

    CredentialModel matched =
        PatUtils.streamPats(user)
            .filter(cred -> PatUtils.verifyToken(submittedToken, PatUtils.extractHash(cred)))
            .findFirst()
            .orElse(null);

    if (matched == null) {
      PatUtils.recordFailedAttempt(session, realm, user);
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    PatCredentialData data = PatUtils.extractCredentialData(matched);

    if (PatUtils.isExpired(data.expires())) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    if (!PatUtils.userHasAllRoles(user, data.roles())) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    if (requiredRole != null && !requiredRole.isBlank() && !data.roles().contains(requiredRole)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    return authSuccessResponse(user, data);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private UserModel requireAuthenticatedUser() {
    AuthenticationManager.AuthResult auth =
        new AppAuthManager.BearerTokenAuthenticator(session).authenticate();
    if (auth == null || auth.getUser() == null) {
      throw new NotAuthorizedException("Bearer token required");
    }
    return auth.getUser();
  }

  private static String[] parseBasicCredentials(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Basic ")) return null;
    return decodeBasic(authHeader);
  }

  private static Response authSuccessResponse(UserModel user, PatCredentialData data) {
    String rolesHeader = String.join(",", data.roles());
    return Response.ok()
        .type(MediaType.TEXT_PLAIN_TYPE)
        .header("X-User", user.getUsername())
        .header("X-User-Id", user.getId())
        .header("X-Roles", rolesHeader)
        .build();
  }

  private static String[] decodeBasic(String header) {
    try {
      String encoded = header.substring("Basic ".length()).trim();
      String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
      int colon = decoded.indexOf(':');
      if (colon < 0) return null;
      return new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)};
    } catch (Exception e) {
      return null;
    }
  }
}
