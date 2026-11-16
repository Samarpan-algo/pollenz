package com.pollenz.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.pollenz.auth.JwtAuthenticationFilter;
import com.pollenz.auth.JwtService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless JWT security. Errors use the SPEC JSON shape {status, error, message}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/api/auth/**", "/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
						.permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(entryPoint())
						.accessDeniedHandler(accessDeniedHandler()))
				.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${pollenz.cors-origin}") String origin) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(origin));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	private AuthenticationEntryPoint entryPoint() {
		return (request, response, ex) -> writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
				"Authentication required");
	}

	private AccessDeniedHandler accessDeniedHandler() {
		return (request, response, ex) -> writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
	}

	// Fixed ASCII strings only, so hand-built JSON is safe and needs no ObjectMapper.
	private static void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
			throws java.io.IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"status\":" + status.value() + ",\"error\":\"" + code
				+ "\",\"message\":\"" + message + "\"}");
	}

}
