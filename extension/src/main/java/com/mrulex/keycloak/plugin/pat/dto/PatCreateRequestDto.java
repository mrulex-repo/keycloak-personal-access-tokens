package com.mrulex.keycloak.plugin.pat.dto;

import java.util.List;

public record PatCreateRequestDto(String name, String expires, List<String> roles) {}
