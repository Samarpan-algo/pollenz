package com.pollenz.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Both fields optional: null means "leave unchanged". An empty {@code upiId} clears it.
 */
public record UpdateProfileRequest(
		@Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
		@Size(max = 100) @Pattern(regexp = "|[A-Za-z0-9._-]{2,}@[A-Za-z][A-Za-z0-9.-]{1,}",
				message = "must look like name@handle") String upiId) {
}
