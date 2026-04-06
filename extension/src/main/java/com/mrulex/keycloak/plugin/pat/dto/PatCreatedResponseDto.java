package com.mrulex.keycloak.plugin.pat.dto;

import java.util.List;

public record PatCreatedResponseDto(
        String id,
        String name,
        String token,
        String created,
        String expires,
        List<String> roles
) {}
