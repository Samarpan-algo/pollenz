package com.pollenz.user;

import java.time.Instant;

public record UserResponse(Long id, String name, String email, String upiId, Instant createdAt) {

	public static UserResponse from(User u) {
		return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getUpiId(), u.getCreatedAt());
	}

}
