package com.pollenz.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, max = 72) String password) {

	public RegisterRequest {
		// Trim before validation so " a@b.com " is accepted and stored as "a@b.com".
		email = email == null ? null : email.trim();
	}
}
