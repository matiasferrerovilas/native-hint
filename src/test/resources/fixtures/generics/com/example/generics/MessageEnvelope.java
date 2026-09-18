package com.example.generics;

public record MessageEnvelope<T>(String eventType, T message) {
}
