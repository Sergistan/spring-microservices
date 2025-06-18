package com.utochkin.shopservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
//@OpenAPIDefinition(
//        info = @Info(title = "Order Service API", version = "1.0"),
//        security = @SecurityRequirement(name = "bearerAuth")
//)
//@SecurityScheme(
//        name = "bearerAuth",
//        type = SecuritySchemeType.OAUTH2,
//        bearerFormat = "JWT",
//        scheme = "bearer",
//        flows = @OAuthFlows(
//                authorizationCode = @OAuthFlow(
//                        authorizationUrl = "http://localhost:8080/realms/microservices-realm/protocol/openid-connect/auth",
//                        tokenUrl = "http://localhost:8080/realms/microservices-realm/protocol/openid-connect/token",
//                        scopes = {
//                                @OAuthScope(name = "openid", description = "Доступ к OpenID"),
//                                @OAuthScope(name = "profile", description = "Доступ к профилю")
//                        }
//                )
//        )
//)
//public class OpenApiConfig {
//
//    @Bean
//    public OpenApiCustomizer globalOpenApiCustomizer() {
//        return openApi -> {
//            openApi.getInfo().setTitle("My Shop API");
//            openApi.getInfo().setVersion("1.0");
//            openApi.getInfo().setDescription("Документация API магазина товаров");
//        };
//    }
//}

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "My Shop API",
                version     = "1.0",
                description = "Документация API магазина товаров"
        ),
        security = @SecurityRequirement(name = "oauth2"),
        servers = {
                @Server(url = "/")
        }
)
@SecurityScheme(
        name        = "oauth2",
        type        = SecuritySchemeType.OAUTH2,
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
            openApi.getInfo().setTitle("My Shop API");
            openApi.getInfo().setVersion("1.0");
            openApi.getInfo().setDescription("Документация API магазина товаров");
        };
    }
}
