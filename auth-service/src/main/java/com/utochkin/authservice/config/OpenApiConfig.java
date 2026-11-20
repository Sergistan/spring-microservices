package com.utochkin.authservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI-конфигурация auth-service — копия паттерна order/shop-service: объявляет OAuth2-схему
 * (authorization_code через getaway-server), благодаря чему во вкладке Auth Service Swagger UI
 * появляется кнопка Authorize — без неё DELETE /users/me (и GET /users/me) не с чем отправить JWT.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "Auth Service API",
                version     = "1.0",
                description = "Документация API регистрации (покупатель/курьер) и удаления собственного аккаунта"
        ),
        security = @SecurityRequirement(name = "oauth2"),
        servers = {
                @Server(url = "/")
        }
)
@SecurityScheme(
        name        = "oauth2",
        type = SecuritySchemeType.OAUTH2,
        bearerFormat = "JWT",
        scheme = "bearer",
        flows       = @OAuthFlows(
                authorizationCode = @OAuthFlow(
                        authorizationUrl = "http://localhost:8072/oauth2/authorization/keycloak",
                        tokenUrl         = "http://localhost:8072/login/oauth2/code/keycloak",
                        scopes = {
                                @OAuthScope(name = "openid",  description = "Доступ к OpenID"),
                                @OAuthScope(name = "profile", description = "Доступ к профилю")
                        }
                )
        )
)
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer globalOpenApiCustomizer() {
        return openApi -> {
            openApi.getInfo().setTitle("Auth Service API");
            openApi.getInfo().setVersion("1.0");
            openApi.getInfo().setDescription("Документация API регистрации (покупатель/курьер) и удаления собственного аккаунта");
        };
    }
}
