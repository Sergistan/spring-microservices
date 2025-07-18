package com.utochkin.getawayserver;

import com.utochkin.getawayserver.config.AuthServerRoleConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServerRoleConverterTest {

    Converter<Jwt, Collection<GrantedAuthority>> converter = new AuthServerRoleConverter();

    @Test
    @DisplayName("Когда нет ResourceAccess возвращает пустые Authorities")
    void whenNoResourceAccess_thenEmptyAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("resource_access", Map.of())
                .build();

        Collection<GrantedAuthority> auths = converter.convert(jwt);
        assertThat(auths).isEmpty();
    }

    @Test
    @DisplayName("Когда есть Roles тогда есть префикс с ROLE_")
    void whenHasRoles_thenPrefixWithROLE() {
        Map<String,Object> resourceAccess = Map.of(
                "spring-microservices", Map.of("roles",
                        java.util.List.of("user", "admin"))
        );
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("resource_access", resourceAccess)
                .build();

        Collection<GrantedAuthority> auths = converter.convert(jwt);
        assertThat(auths).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_user", "ROLE_admin");
    }
}
