package com.utochkin.historyservice;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(
        properties = {
                "spring.profiles.active=test",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
                "spring.cloud.compatibility-verifier.enabled=false"
        }
)
class HistoryServiceApplicationTests {

    @TestConfiguration
    static class SecurityTestConfig {
        @Bean
        public JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }
    }

    @Test
    void contextLoads() {
    }

}
