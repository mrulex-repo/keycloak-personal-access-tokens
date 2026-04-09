package com.mrulex.keycloak.plugin.pat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;

class PersonalAccessTokenProviderFactoryTest {

  private final PersonalAccessTokenProviderFactory factory =
      new PersonalAccessTokenProviderFactory();

  @Test
  void getId_returnsExpected() {
    assertThat(factory.getId()).isEqualTo(PersonalAccessTokenProviderFactory.ID);
  }

  @Test
  void create_returnsProvider() {
    assertThat(factory.create(mock(KeycloakSession.class)))
        .isInstanceOf(PersonalAccessTokenProvider.class);
  }

  @Test
  void close_noOp() {
    factory.close();
  }
}
