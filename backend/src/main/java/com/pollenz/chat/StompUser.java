package com.pollenz.chat;

import java.security.Principal;

/** The authenticated STOMP session user. The principal name is the user id as text. */
public record StompUser(long userId) implements Principal {

	@Override
	public String getName() {
		return Long.toString(userId);
	}

}
