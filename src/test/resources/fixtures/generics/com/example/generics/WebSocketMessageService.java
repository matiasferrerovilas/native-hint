package com.example.generics;

public abstract class WebSocketMessageService {
	public void publish(Object result, String topic) {
		var event = new MessageEnvelope<>("SOME_TYPE", result);
		System.out.println(event + topic);
	}
}
