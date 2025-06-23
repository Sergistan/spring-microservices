package com.utochkin.eurekaserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		properties = {
				"spring.profiles.active=test",
				"eureka.client.enabled=false",
				"eureka.client.register-with-eureka=false",
				"eureka.client.fetch-registry=false"
		}
)
class EurekaServerApplicationTests {
	@Test
	void contextLoads() { }
}
