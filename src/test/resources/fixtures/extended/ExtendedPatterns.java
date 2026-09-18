package com.example.extended;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

@RestController
class NotificationService {
	private final ApplicationEventPublisher eventPublisher;

	NotificationService(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	void publish(NotificationEvent event) {
		eventPublisher.publishEvent(event);
	}

	@EventListener
	void onNotification(NotificationEvent event) {
	}

	@GetMapping
	Object search(@ParameterObject SearchFilterRecord filter) {
		return null;
	}
}

record NotificationEvent(String message) {
}

record SearchFilterRecord(String from, String to) {
}

@HttpExchange
interface ExternalUsersClient {
	@GetExchange("/v1/users")
	List<UserProfile> getUsers();
}

record UserProfile(String id) {
}

class PositiveAmountValidator implements ConstraintValidator<ValidPositiveAmount, Object> {
	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		return true;
	}
}

@interface ValidPositiveAmount {
}
