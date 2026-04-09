package com.mrulex.keycloak.plugin.pat;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class PersonalAccessTokenProviderFactory implements RealmResourceProviderFactory {

  public static final String ID = "personal-access-token";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public RealmResourceProvider create(KeycloakSession session) {
    return new PersonalAccessTokenProvider(session);
  }

  @Override
  public void init(Config.Scope config) {
    // nothing to configure
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // nothing to post-initialize
  }

  @Override
  public void close() {
    // nothing to close
  }
}
