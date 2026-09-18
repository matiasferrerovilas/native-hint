package com.example.publish;

import com.example.events.OrderCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class OrderPublisher {

	private final RabbitTemplate rabbitTemplate;

	public OrderPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publish(OrderCreatedEvent event) {
		rabbitTemplate.convertAndSend("orders", "order.created", event);
	}
}
