package dev.nativehint;

public record HintCandidate(String typeName, Reason reason, String file, int line, String detail) {

	public enum Reason {
		RABBIT_CONVERT_AND_SEND,
		RABBIT_OR_STOMP_LISTENER_PARAMETER,
		SPEL_TYPE_REFERENCE,
		APPLICATION_EVENT_PUBLISHED,
		EVENT_LISTENER_PARAMETER,
		PARAMETER_OBJECT_BINDING,
		HTTP_EXCHANGE_RETURN_TYPE,
		CONSTRAINT_VALIDATOR,
	}
}
