package com.mrulex.keycloak.plugin.pat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;

class PatBasicAuthAuthenticatorFactoryTest {

  private final PatBasicAuthAuthenticatorFactory factory = new PatBasicAuthAuthenticatorFactory();

  @Test
  void getDisplayType_returnsNonNull() {
    assertThat(factory.getDisplayType()).isNotNull();
  }

  @Test
  void getReferenceCategory_returnsExpected() {
    assertThat(factory.getReferenceCategory()).isEqualTo("pat");
  }

  @Test
  void isConfigurable_returnsFalse() {
    assertThat(factory.isConfigurable()).isFalse();
  }

  @Test
  void getRequirementChoices_isNotEmpty() {
    assertThat(factory.getRequirementChoices()).isNotEmpty();
  }

  @Test
  void isUserSetupAllowed_returnsFalse() {
    assertThat(factory.isUserSetupAllowed()).isFalse();
  }

  @Test
  void getHelpText_returnsNonNull() {
    assertThat(factory.getHelpText()).isNotNull();
  }

  @Test
  void getConfigProperties_returnsEmpty() {
    assertThat(factory.getConfigProperties()).isEmpty();
  }

  @Test
  void close_noOp() {
    factory.close();
  }

  @Test
  void create_returnsAuthenticatorInstance() {
    assertThat(factory.create(mock(KeycloakSession.class)))
        .isInstanceOf(PatBasicAuthAuthenticator.class);
  }
}
