package com.utochkin.notificationservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthServerRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null || resourceAccess.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get("spring-microservices");
        if (clientAccess == null || clientAccess.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> roles = (List<String>) clientAccess.get("roles");
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }

        return roles.stream()
                .map(roleName -> "ROLE_" + roleName) // Добавляем префикс ROLE_
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
