package com.pollenz.chat;

/** Body of a STOMP SEND to {@code /app/pools/{id}/chat}. */
public record ChatRequest(String body) {
}
