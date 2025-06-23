package com.utochkin.getawayserver;

import com.utochkin.getawayserver.config.ReactiveConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
                "JWT_PUBLIC_KEY=…",
                "spring.security.oauth2.client.registration.keycloak.client-id=dummy",
                "spring.security.oauth2.client.registration.keycloak.client-secret=dummy",
                "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code",
                "spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
                "spring.security.oauth2.client.provider.keycloak.issuer-uri=http://example.com",
                "spring.cloud.compatibility-verifier.enabled=false"
        }
)
@ImportAutoConfiguration(exclude = ReactiveConfig.class)
@ActiveProfiles("test")
class GetawayServerApplicationTests {

    @TestConfiguration
    static class SecurityStubs {
        @Bean
        public ReactiveJwtDecoder jwtDecoder() {
            return Mockito.mock(ReactiveJwtDecoder.class);
        }
        @Bean
        public ReactiveClientRegistrationRepository clientRegistrations() {
            return Mockito.mock(ReactiveClientRegistrationRepository.class);
        }
        @Bean
        public ReactiveOAuth2AuthorizedClientService authClientService() {
            return Mockito.mock(ReactiveOAuth2AuthorizedClientService.class);
        }
    }


    @Test
    void contextLoads() {
    }

}
