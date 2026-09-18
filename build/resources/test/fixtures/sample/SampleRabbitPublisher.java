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

	public void publishInvitationCreated(InvitationCreatedEvent event) {
		rabbitTemplate.convertAndSend("identity.topic", "invitation.sent", event);
	}

	public void publishFromConstructor() {
		rabbitTemplate.convertAndSend("identity.topic", "member.removed", new MemberRemovedEvent("w1", "u1"));
	}

	@RabbitListener(queues = "invitation-queue")
	public void onInvitationAccepted(InvitationAcceptedEvent event) {
		System.out.println(event);
	}

	@PreAuthorize("T(com.example.security.RoleChecker).isAdmin(authentication)")
	public void adminOnly() {
	}
}
