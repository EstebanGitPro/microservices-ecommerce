package com.ecommerce.notification_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Sends and receives a message through a real broker, so a RabbitMQ client
 * upgrade that breaks Spring AMQP fails here instead of in production.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RabbitRoundTripTests {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Test
	void sendsAndReceivesMessage() {
		String queue = amqpAdmin.declareQueue(new AnonymousQueue());

		rabbitTemplate.convertAndSend(queue, "order-created");

		assertThat(rabbitTemplate.receiveAndConvert(queue, 5_000)).isEqualTo("order-created");
	}

}
