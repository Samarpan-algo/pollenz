package com.pollenz.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads {@code Authorization: Bearer <jwt>} and stores the user id (a {@link Long}) as the principal.
 * An invalid token leaves the context empty; the entry point then answers 401.
 * Not a Spring bean on purpose: SecurityConfig adds it to the security chain only.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIX = "Bearer ";

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(PREFIX)) {
			jwtService.parseUserId(header.substring(PREFIX.length()).trim()).ifPresent(userId -> {
				var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
				SecurityContextHolder.getContext().setAuthentication(auth);
			});
		}
		chain.doFilter(request, response);
	}

}
