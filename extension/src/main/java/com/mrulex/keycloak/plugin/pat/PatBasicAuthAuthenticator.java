package com.mrulex.keycloak.plugin.pat;

import com.mrulex.keycloak.plugin.pat.dto.PatCredentialData;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.events.Errors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.BruteForceProtector;

/**
 * Keycloak Authenticator for the Direct Grant flow that accepts Personal Access Tokens as passwords
 * (submitted via standard ROPC username/password form parameters).
 *
 * <p>Bind this authenticator to a dedicated "pat-direct-grant" flow and assign that flow only to
 * clients that should accept PATs via OIDC Direct Grant.
 */
public class PatBasicAuthAuthenticator implements Authenticator {

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    MultivaluedMap<String, String> formParams = context.getHttpRequest().getDecodedFormParameters();
    String username = formParams.getFirst("username");
    String submittedToken = formParams.getFirst("password");

    if (username == null || submittedToken == null) {
      context.failure(
          AuthenticationFlowError.INVALID_CREDENTIALS,
          errorChallenge(401, "invalid_grant", "Invalid user credentials"));
      return;
    }

    UserModel user = resolveUser(context, username);
    if (user == null) return;

    if (isBruteForceBlocked(context, user)) return;

    CredentialModel matched = findMatchingPat(context, user, submittedToken);
    if (matched == null) return;

    PatCredentialData data = PatUtils.extractCredentialData(matched);
    if (isTokenExpired(context, data)) return;

    context
        .getAuthenticationSession()
        .setUserSessionNote("pat_roles", String.join(",", data.roles()));
    context.getAuthenticationSession().setUserSessionNote("pat_id", matched.getId());
    context.setUser(user);
    context.success();
  }

  private UserModel resolveUser(AuthenticationFlowContext context, String username) {
    UserModel user = context.getSession().users().getUserByUsername(context.getRealm(), username);
    if (user == null) {
      context.getEvent().error(Errors.USER_NOT_FOUND);
      context.failure(
          AuthenticationFlowError.INVALID_USER,
          errorChallenge(401, "invalid_grant", "Invalid user credentials"));
    }
    return user;
  }

  private boolean isBruteForceBlocked(AuthenticationFlowContext context, UserModel user) {
    KeycloakSession session = context.getSession();
    BruteForceProtector protector = session.getProvider(BruteForceProtector.class);
    if (protector != null && protector.isTemporarilyDisabled(session, context.getRealm(), user)) {
      context.getEvent().error(Errors.USER_TEMPORARILY_DISABLED);
      context.failure(
          AuthenticationFlowError.USER_TEMPORARILY_DISABLED,
          errorChallenge(401, "invalid_grant", "Account temporarily locked"));
      return true;
    }
    return false;
  }

  private CredentialModel findMatchingPat(
      AuthenticationFlowContext context, UserModel user, String submittedToken) {
    CredentialModel matched =
        PatUtils.streamPats(user)
            .filter(cred -> PatUtils.verifyToken(submittedToken, PatUtils.extractHash(cred)))
            .findFirst()
            .orElse(null);
    if (matched == null) {
      context.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
      PatUtils.recordFailedAttempt(context.getSession(), context.getRealm(), user);
      context.failure(
          AuthenticationFlowError.INVALID_CREDENTIALS,
          errorChallenge(401, "invalid_grant", "Invalid user credentials"));
    }
    return matched;
  }

  private boolean isTokenExpired(AuthenticationFlowContext context, PatCredentialData data) {
    if (PatUtils.isExpired(data.expires())) {
      context.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
      context.failure(
          AuthenticationFlowError.EXPIRED_CODE,
          errorChallenge(401, "invalid_grant", "Token has expired"));
      return true;
    }
    return false;
  }

  private static Response errorChallenge(int status, String error, String description) {
    String body = "{\"error\":\"" + error + "\",\"error_description\":\"" + description + "\"}";
    return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON_TYPE).build();
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    // Non-interactive authenticator — no action phase
  }

  @Override
  public boolean requiresUser() {
    return false;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    // No required actions
  }

  @Override
  public void close() {}
}
