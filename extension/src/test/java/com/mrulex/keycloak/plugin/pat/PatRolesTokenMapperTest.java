package com.mrulex.keycloak.plugin.pat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;

class PatRolesTokenMapperTest {

  private final PatRolesTokenMapper mapper = new PatRolesTokenMapper();

  @Test
  void getDisplayType_returnsNonNull() {
    assertThat(mapper.getDisplayType()).isNotNull();
  }

  @Test
  void getDisplayCategory_returnsNonNull() {
    assertThat(mapper.getDisplayCategory()).isNotNull();
  }

  @Test
  void getHelpText_returnsNonNull() {
    assertThat(mapper.getHelpText()).isNotNull();
  }

  @Test
  void getConfigProperties_returnsEmpty() {
    assertThat(mapper.getConfigProperties()).isEmpty();
  }

  @Test
  void setClaim_noOp() {
    // Protected empty override — verify it does not throw
    mapper.setClaim(
        mock(IDToken.class),
        mock(ProtocolMapperModel.class),
        mock(UserSessionModel.class),
        mock(KeycloakSession.class),
        mock(ClientSessionContext.class));
  }

  @Test
  void transformAccessToken_withPatRoles_replacesRealmAccess() {
    UserSessionModel session = mock(UserSessionModel.class);
    when(session.getNote("pat_roles")).thenReturn("maven-read,maven-deploy");

    AccessToken token = new AccessToken();
    AccessToken result =
        mapper.transformAccessToken(
            token,
            mock(ProtocolMapperModel.class),
            mock(KeycloakSession.class),
            session,
            mock(ClientSessionContext.class));

    assertThat(result.getRealmAccess().getRoles())
        .containsExactlyInAnyOrder("maven-read", "maven-deploy");
  }

  @Test
  void transformAccessToken_withoutPatRoles_isNoop() {
    UserSessionModel session = mock(UserSessionModel.class);
    when(session.getNote("pat_roles")).thenReturn(null);

    AccessToken token = new AccessToken();
    AccessToken.Access original = new AccessToken.Access();
    original.roles(Set.of("user", "admin"));
    token.setRealmAccess(original);

    AccessToken result =
        mapper.transformAccessToken(
            token,
            mock(ProtocolMapperModel.class),
            mock(KeycloakSession.class),
            session,
            mock(ClientSessionContext.class));

    assertThat(result.getRealmAccess().getRoles()).containsExactlyInAnyOrder("user", "admin");
  }

  @Test
  void transformAccessToken_withBlankPatRoles_isNoop() {
    UserSessionModel session = mock(UserSessionModel.class);
    when(session.getNote("pat_roles")).thenReturn("  ");

    AccessToken token = new AccessToken();
    AccessToken result =
        mapper.transformAccessToken(
            token,
            mock(ProtocolMapperModel.class),
            mock(KeycloakSession.class),
            session,
            mock(ClientSessionContext.class));

    assertThat(result.getRealmAccess()).isNull();
  }
}
