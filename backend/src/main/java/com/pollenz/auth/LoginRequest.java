package com.pollenz.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank String email,
		@NotBlank @Size(max = 72) String password) {

	public LoginRequest {
		// Trim before validation so " a@b.com " is accepted and stored as "a@b.com".
		email = email == null ? null : email.trim();
	}
}
