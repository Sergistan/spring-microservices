package com.utochkin.configserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=native",
                "spring.cloud.config.server.native.enabled=true",
                "spring.cloud.config.server.native.searchLocations=classpath:/",
                "eureka.client.enabled=false",
                "spring.cloud.config.discovery.enabled=false"
        }
)
class ConfigServerApplicationTests {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("Проверка /actuator/health")
    void healthEndpointUp() {
        var res = rest.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("Проверка получения корректных данных из application.yml")
    void servePropertiesFromNativeRepo() {
        var res = rest.getForEntity("http://localhost:" + port + "/application/default", String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).contains("foo").contains("bar");
    }
}
