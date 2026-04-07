package com.mrulex.keycloak.plugin.pat;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class PersonalAccessTokenProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public PersonalAccessTokenProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new PersonalAccessTokenResource(session);
    }

    @Override
    public void close() {
        // nothing to close
    }
}
