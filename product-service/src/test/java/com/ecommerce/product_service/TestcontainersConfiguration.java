package com.ecommerce.product_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * MongoConfig builds the client from spring.data.mongodb.* properties, so the
 * container values are registered as properties instead of using @ServiceConnection.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	private static final int MONGO_PORT = 27017;
	private static final String USERNAME = "root";
	private static final String PASSWORD = "password";

	@Bean
	GenericContainer<?> mongoContainer() {
		return new GenericContainer<>(DockerImageName.parse("mongo:7.0.4"))
				.withEnv("MONGO_INITDB_ROOT_USERNAME", USERNAME)
				.withEnv("MONGO_INITDB_ROOT_PASSWORD", PASSWORD)
				.withExposedPorts(MONGO_PORT)
				// Mongo restarts once after creating the root user
				.waitingFor(Wait.forLogMessage("(?i).*waiting for connections.*", 2));
	}

	@Bean
	DynamicPropertyRegistrar mongoProperties(GenericContainer<?> mongoContainer) {
		return registry -> {
			registry.add("spring.data.mongodb.host", mongoContainer::getHost);
			registry.add("spring.data.mongodb.port", () -> mongoContainer.getMappedPort(MONGO_PORT));
			registry.add("spring.data.mongodb.database", () -> "product-db");
			registry.add("spring.data.mongodb.username", () -> USERNAME);
			registry.add("spring.data.mongodb.password", () -> PASSWORD);
			registry.add("spring.data.mongodb.authentication-database", () -> "admin");
		};
	}

}
