package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;

class AuthIntegrationTest extends IntegrationTestBase {

	@Test
	void registerReturnsTokenAndUserWithoutPasswordHash() throws Exception {
		String email = uniqueEmail();
		ResultActions res = postJson("/api/auth/register", null, registerBody("Asha", email, PASSWORD))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.user.email").value(email))
				.andExpect(jsonPath("$.user.name").value("Asha"))
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.user.password_hash").doesNotExist());
		assertThat(res.andReturn().getResponse().getContentAsString()).doesNotContain("$2");
	}

	@Test
	void registerNormalizesEmailAndLoginIsCaseInsensitive() throws Exception {
		String email = uniqueEmail();
		postJson("/api/auth/register", null, registerBody("Asha", "  " + email.toUpperCase() + " ", PASSWORD))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user.email").value(email));
		postJson("/api/auth/login", null, """
				{"email":"%s","password":"%s"}""".formatted(email.toUpperCase(), PASSWORD))
				.andExpect(status().isOk());
	}

	@Test
	void loginHappyPathReturnsUsableToken() throws Exception {
		String email = uniqueEmail();
		registerToken("Ravi", email);
		JsonNode body = json(postJson("/api/auth/login", null, """
				{"email":"%s","password":"%s"}""".formatted(email, PASSWORD)).andExpect(status().isOk()));
		String token = body.get("token").asString();
		getAuth("/api/users/me", token).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void duplicateEmailIsConflict() throws Exception {
		String email = uniqueEmail();
		registerToken("One", email);
		postJson("/api/auth/register", null, registerBody("Two", email.toUpperCase(), PASSWORD))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveSameError() throws Exception {
		String email = uniqueEmail();
		registerToken("Ravi", email);
		String wrongPassword = postJson("/api/auth/login", null, """
				{"email":"%s","password":"wrong-password"}""".formatted(email))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();
		String unknownEmail = postJson("/api/auth/login", null, """
				{"email":"%s","password":"wrong-password"}""".formatted(uniqueEmail()))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		assertThat(unknownEmail).isEqualTo(wrongPassword);
	}

	@Test
	void protectedRouteWithoutTokenIs401InSpecShape() throws Exception {
		mvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void protectedRouteWithGarbageTokenIs401InSpecShape() throws Exception {
		getAuth("/api/users/me", "not.a.jwt")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	@Test
	void registerValidationFailuresAre400() throws Exception {
		postJson("/api/auth/register", null, registerBody("Asha", "not-an-email", PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
		postJson("/api/auth/register", null, registerBody("Asha", uniqueEmail(), "short"))
				.andExpect(status().isBadRequest());
		postJson("/api/auth/register", null, registerBody("Asha", uniqueEmail(), "x".repeat(73)))
				.andExpect(status().isBadRequest());
		postJson("/api/auth/register", null, registerBody("", uniqueEmail(), PASSWORD))
				.andExpect(status().isBadRequest());
		postJson("/api/auth/register", null, registerBody("n".repeat(101), uniqueEmail(), PASSWORD))
				.andExpect(status().isBadRequest());
	}

	@Test
	void openApiDocsArePublicAndDeclareBearerAuth() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
	}

}
