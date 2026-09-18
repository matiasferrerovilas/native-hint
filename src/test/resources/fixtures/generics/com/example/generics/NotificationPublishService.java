package com.example.generics;

public class NotificationPublishService extends WebSocketMessageService {
	public void publishCustomerUpdated(CustomerProfile customerProfile) {
		this.publish(customerProfile, "topic/customer");
	}
}
