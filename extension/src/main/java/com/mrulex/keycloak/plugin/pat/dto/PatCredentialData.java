package com.mrulex.keycloak.plugin.pat.dto;

import java.util.List;

/**
 * Internal DTO for the JSON stored in {@code CredentialModel#credentialData}.
 * Never exposed via REST.
 */
public record PatCredentialData(List<String> roles, String expires) {}
