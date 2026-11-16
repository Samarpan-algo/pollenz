package com.pollenz.chat;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.pollenz.common.ApiError;
import com.pollenz.common.ApiException;

/**
 * STOMP chat endpoint. The interceptor has already checked membership; the service checks again.
 * A rejected message is answered on {@code /user/queue/errors} and the connection stays open.
 */
@Controller
public class ChatMessageController {

	private static final Logger log = LoggerFactory.getLogger(ChatMessageController.class);

	private final ChatService service;
	private final PoolEventBroadcaster broadcaster;

	public ChatMessageController(ChatService service, PoolEventBroadcaster broadcaster) {
		this.service = service;
		this.broadcaster = broadcaster;
	}

	@MessageMapping("/pools/{id}/chat")
	public void chat(@DestinationVariable long id, ChatRequest request, Principal principal) {
		long userId = ((StompUser) principal).userId();
		// post() commits its transaction before it returns, so the broadcast follows the commit.
		broadcaster.chatMessage(service.post(userId, id, request == null ? null : request.body()));
	}

	@MessageExceptionHandler
	@SendToUser(destinations = "/queue/errors", broadcast = false)
	public ApiError onError(Exception ex) {
		if (ex instanceof ApiException api) {
			return new ApiError(api.getStatus().value(), api.getCode(), api.getMessage());
		}
		log.warn("Chat message rejected", ex);
		return new ApiError(HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED", "Invalid chat message");
	}

}
