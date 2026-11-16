package com.pollenz.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Issues and verifies HS256 JWTs. Subject is the user id. */
@Service
public class JwtService {

	private final SecretKey key;
	private final Duration expiry;

	public JwtService(@Value("${pollenz.jwt.secret}") String secret,
			@Value("${pollenz.jwt.expiry-hours:24}") long expiryHours) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HS256");
		}
		this.key = Keys.hmacShaKeyFor(bytes);
		this.expiry = Duration.ofHours(expiryHours);
	}

	public String issue(long userId) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(Long.toString(userId))
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expiry)))
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	/** Returns the user id, or empty when the token is malformed, tampered, expired or not HS256. */
	public Optional<Long> parseUserId(String token) {
		try {
			var jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
			if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
				return Optional.empty();
			}
			return Optional.of(Long.parseLong(jws.getPayload().getSubject()));
		}
		catch (JwtException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
