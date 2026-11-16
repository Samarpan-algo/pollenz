package com.pollenz.auth;

import java.time.Instant;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pollenz.common.ApiException;
import com.pollenz.user.User;
import com.pollenz.user.UserRepository;
import com.pollenz.user.UserResponse;

@Service
public class AuthService {

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final JwtService jwt;

	public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
		this.users = users;
		this.encoder = encoder;
		this.jwt = jwt;
	}

	@Transactional
	public AuthResponse register(RegisterRequest req) {
		String email = normalize(req.email());
		if (users.existsByEmail(email)) {
			throw emailTaken();
		}
		User user = new User(req.name().trim(), email, encoder.encode(req.password()), Instant.now());
		try {
			users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Concurrent registration with the same email: the unique constraint is the final guard.
			throw emailTaken();
		}
		return new AuthResponse(jwt.issue(user.getId()), UserResponse.from(user));
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest req) {
		User user = users.findByEmail(normalize(req.email()))
				.filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
						"Invalid email or password"));
		return new AuthResponse(jwt.issue(user.getId()), UserResponse.from(user));
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
	}

}
