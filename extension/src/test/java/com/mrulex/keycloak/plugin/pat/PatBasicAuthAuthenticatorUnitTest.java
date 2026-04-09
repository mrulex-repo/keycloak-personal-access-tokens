package com.mrulex.keycloak.plugin.pat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

class PatBasicAuthAuthenticatorUnitTest {

  private final PatBasicAuthAuthenticator authenticator = new PatBasicAuthAuthenticator();

  @Test
  void requiresUser_returnsFalse() {
    assertThat(authenticator.requiresUser()).isFalse();
  }

  @Test
  void configuredFor_returnsTrue() {
    assertThat(
            authenticator.configuredFor(
                mock(KeycloakSession.class), mock(RealmModel.class), mock(UserModel.class)))
        .isTrue();
  }

  @Test
  void setRequiredActions_noOp() {
    authenticator.setRequiredActions(
        mock(KeycloakSession.class), mock(RealmModel.class), mock(UserModel.class));
  }

  @Test
  void action_noOp() {
    authenticator.action(mock(AuthenticationFlowContext.class));
  }

  @Test
  void close_noOp() {
    authenticator.close();
  }
}
