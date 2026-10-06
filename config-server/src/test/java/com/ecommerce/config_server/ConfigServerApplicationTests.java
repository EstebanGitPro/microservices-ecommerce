package com.ecommerce.config_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The native profile serves config from the classpath, so the test does not
 * clone the private Git repository or need GitHub credentials.
 */
@ActiveProfiles("native")
@SpringBootTest
class ConfigServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
