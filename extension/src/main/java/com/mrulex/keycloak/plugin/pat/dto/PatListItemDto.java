package com.mrulex.keycloak.plugin.pat.dto;

import java.util.List;

public record PatListItemDto(
    String id, String name, String created, String expires, List<String> roles) {}
