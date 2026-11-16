package com.pollenz.chat;

import java.time.Instant;

/** A chat message as returned by the history endpoint and sent as the CHAT_MESSAGE payload. */
public record ChatMessageResponse(long id, long poolId, long senderId, String senderName, String body,
		Instant createdAt) {
}
