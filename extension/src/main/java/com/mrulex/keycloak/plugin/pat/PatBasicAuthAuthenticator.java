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
 * Keycloak Authenticator for the Direct Grant flow that accepts Personal Access Tokens
 * as passwords (submitted via standard ROPC username/password form parameters).
 *
 * Bind this authenticator to a dedicated "pat-direct-grant" flow and assign that
 * flow only to clients that should accept PATs via OIDC Direct Grant.
 */
public class PatBasicAuthAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formParams = context.getHttpRequest().getDecodedFormParameters();
        String username = formParams.getFirst("username");
        String submittedToken = formParams.getFirst("password");

        if (username == null || submittedToken == null) {
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS,
                    errorChallenge(401, "invalid_grant", "Invalid user credentials"));
            return;
        }

        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        UserModel user = session.users().getUserByUsername(realm, username);
        if (user == null) {
            context.getEvent().error(Errors.USER_NOT_FOUND);
            context.failure(AuthenticationFlowError.INVALID_USER,
                    errorChallenge(401, "invalid_grant", "Invalid user credentials"));
            return;
        }

        // Brute-force check before expensive hash verification
        BruteForceProtector protector = session.getProvider(BruteForceProtector.class);
        if (protector != null && protector.isTemporarilyDisabled(session, realm, user)) {
            context.getEvent().error(Errors.USER_TEMPORARILY_DISABLED);
            context.failure(AuthenticationFlowError.USER_TEMPORARILY_DISABLED,
                    errorChallenge(401, "invalid_grant", "Account temporarily locked"));
            return;
        }

        CredentialModel matched = PatUtils.streamPats(user)
                .filter(cred -> PatUtils.verifyToken(submittedToken, PatUtils.extractHash(cred)))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            context.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
            PatUtils.recordFailedAttempt(session, realm, user);
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS,
                    errorChallenge(401, "invalid_grant", "Invalid user credentials"));
            return;
        }

        PatCredentialData data = PatUtils.extractCredentialData(matched);

        if (PatUtils.isExpired(data.expires())) {
            context.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
            context.failure(AuthenticationFlowError.EXPIRED_CODE,
                    errorChallenge(401, "invalid_grant", "Token has expired"));
            return;
        }

        // Store PAT roles in user session note — read by PatRolesTokenMapper during token issuance
        context.getAuthenticationSession().setUserSessionNote("pat_roles", String.join(",", data.roles()));
        context.getAuthenticationSession().setUserSessionNote("pat_id", matched.getId());

        context.setUser(user);
        context.success();
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
