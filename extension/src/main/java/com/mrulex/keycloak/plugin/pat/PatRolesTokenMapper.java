package com.mrulex.keycloak.plugin.pat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;

/**
 * Protocol mapper that restricts JWT realm roles to only those assigned to the authenticating
 * Personal Access Token.
 *
 * <p>When the {@code pat_roles} user session note is present (set by {@link
 * PatBasicAuthAuthenticator}), the mapper replaces the token's {@code realm_access.roles} with the
 * PAT's role set. When the note is absent (normal login), the mapper is a no-op and all user roles
 * pass through.
 *
 * <p>Configure this mapper on clients that use the PAT Direct Grant flow with a high priority (e.g.
 * 1000) so it runs after the built-in realm-roles mapper has already populated {@code
 * realm_access.roles}.
 */
public class PatRolesTokenMapper extends AbstractOIDCProtocolMapper
    implements OIDCAccessTokenMapper {

  public static final String PROVIDER_ID = "pat-roles-token-mapper";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "PAT Roles Filter";
  }

  @Override
  public String getDisplayCategory() {
    return TOKEN_MAPPER_CATEGORY;
  }

  @Override
  public String getHelpText() {
    return "Restricts JWT realm roles to only those assigned to the authenticating Personal Access Token.";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return List.of();
  }

  /**
   * Sets {@code realm_access.roles} to exactly the PAT's role set when a PAT authentication session
   * is detected. This mapper is intended for use on clients where the built-in "roles" client scope
   * has been removed so that role population is owned entirely by this mapper.
   *
   * <p>When {@code pat_roles} is absent (normal login), the mapper is a no-op.
   */
  @Override
  public AccessToken transformAccessToken(
      AccessToken token,
      ProtocolMapperModel mappingModel,
      KeycloakSession session,
      UserSessionModel userSession,
      ClientSessionContext clientSessionCtx) {
    String patRolesNote = userSession.getNote("pat_roles");
    if (patRolesNote == null || patRolesNote.isBlank()) {
      return token; // normal login — mapper is a no-op
    }

    Set<String> patRoles =
        Arrays.stream(patRolesNote.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());

    // Replace realm_access entirely so the result is deterministic regardless
    // of what other mappers may have already set.
    AccessToken.Access realmAccess = new AccessToken.Access();
    realmAccess.roles(patRoles);
    token.setRealmAccess(realmAccess);
    return token;
  }

  /**
   * No-op — role restriction is handled entirely in {@link #transformAccessToken(AccessToken,
   * ProtocolMapperModel, KeycloakSession, UserSessionModel, ClientSessionContext)}.
   */
  @Override
  protected void setClaim(
      IDToken token,
      ProtocolMapperModel mappingModel,
      UserSessionModel userSession,
      KeycloakSession keycloakSession,
      ClientSessionContext clientSessionCtx) {
    // intentionally empty
  }
}
