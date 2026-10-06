package com.ecommerce.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
class OrderServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
