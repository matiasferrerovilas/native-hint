package dev.nativehint;

public record HintCandidate(String typeName, Reason reason, String file, int line, String detail) {

	public enum Reason {
		RABBIT_CONVERT_AND_SEND,
		RABBIT_OR_STOMP_LISTENER_PARAMETER,
		SPEL_TYPE_REFERENCE,
	}
}
