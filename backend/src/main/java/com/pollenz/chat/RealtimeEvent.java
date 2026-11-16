package com.pollenz.chat;

/** Envelope for everything sent to {@code /topic/pools/{id}}. */
public record RealtimeEvent(String type, Object payload) {
}
