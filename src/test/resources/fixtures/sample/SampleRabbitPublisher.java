package com.example.events;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class SampleRabbitPublisher {

	private final RabbitTemplate rabbitTemplate;

	public SampleRabbitPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publishTicketCreated(TicketCreatedEvent event) {
		rabbitTemplate.convertAndSend("support.topic", "ticket.created", event);
	}

	public void publishFromConstructor() {
		rabbitTemplate.convertAndSend("support.topic", "ticket.closed", new TicketClosedEvent("t1", "u1"));
	}

	@RabbitListener(queues = "ticket-queue")
	public void onTicketAssigned(TicketAssignedEvent event) {
		System.out.println(event);
	}

	@PreAuthorize("T(com.example.security.RoleChecker).isAdmin(authentication)")
	public void adminOnly() {
	}
}
