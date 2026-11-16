package com.pollenz.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService service;

	public UserController(UserService service) {
		this.service = service;
	}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal Long userId) {
		return service.get(userId);
	}

	@PatchMapping("/me")
	public UserResponse update(@AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateProfileRequest req) {
		return service.update(userId, req);
	}

}
