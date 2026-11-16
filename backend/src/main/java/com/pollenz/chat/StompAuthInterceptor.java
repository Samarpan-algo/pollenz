package com.pollenz.chat;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.pollenz.auth.JwtService;

/**
 * Authentication and authorization for STOMP, on the inbound channel.
 * CONNECT needs {@code Authorization: Bearer <jwt>}. After that only these frames are allowed:
 * SUBSCRIBE to {@code /topic/pools/{id}} and SEND to {@code /app/pools/{id}/chat} for members of that
 * pool, and SUBSCRIBE to {@code /user/queue/errors} (the sender's own chat errors). A rejected frame
 * makes the broker answer with an ERROR frame and close the connection.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

	private static final String BEARER = "Bearer ";
	private static final Pattern TOPIC = Pattern.compile("^/topic/pools/(\\d+)$");
	private static final Pattern CHAT = Pattern.compile("^/app/pools/(\\d+)/chat$");
	private static final String ERRORS_QUEUE = "/user/queue/errors";

	private final JwtService jwtService;
	private final ChatService chatService;

	public StompAuthInterceptor(JwtService jwtService, ChatService chatService) {
		this.jwtService = jwtService;
		this.chatService = chatService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		StompCommand command = accessor.getCommand();
		if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
			accessor.setUser(authenticate(accessor));
		}
		else if (StompCommand.SUBSCRIBE.equals(command)) {
			String destination = accessor.getDestination();
			if (!ERRORS_QUEUE.equals(destination)) {
				requireMember(accessor, destination, TOPIC);
			}
		}
		else if (StompCommand.SEND.equals(command)) {
			requireMember(accessor, accessor.getDestination(), CHAT);
		}
		return message;
	}

	private StompUser authenticate(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader("Authorization");
		if (header == null || !header.startsWith(BEARER)) {
			throw new MessageDeliveryException("Missing bearer token");
		}
		Optional<Long> userId = jwtService.parseUserId(header.substring(BEARER.length()).trim());
		return new StompUser(userId.orElseThrow(() -> new MessageDeliveryException("Invalid token")));
	}

	private void requireMember(StompHeaderAccessor accessor, String destination, Pattern pattern) {
		if (!(accessor.getUser() instanceof StompUser user)) {
			throw new MessageDeliveryException("Not authenticated");
		}
		Matcher matcher = destination == null ? null : pattern.matcher(destination);
		if (matcher == null || !matcher.matches()) {
			throw new MessageDeliveryException("Destination not allowed");
		}
		long poolId;
		try {
			poolId = Long.parseLong(matcher.group(1));
		}
		catch (NumberFormatException ex) {
			throw new MessageDeliveryException("Destination not allowed");
		}
		if (!chatService.isPoolMember(user.userId(), poolId)) {
			throw new MessageDeliveryException("Not a member of this pool");
		}
	}

}
