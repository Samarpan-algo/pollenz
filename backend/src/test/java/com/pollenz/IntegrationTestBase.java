package com.pollenz;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared setup for HTTP-level integration tests. Every test registers its own users with unique
 * emails, so tests do not depend on run order or on each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

	protected static final String PASSWORD = "password123";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	protected MockMvc mvc;

	protected static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	protected static JsonNode json(ResultActions result) throws Exception {
		return JSON.readTree(result.andReturn().getResponse().getContentAsString());
	}

	protected ResultActions postJson(String path, String token, String body) throws Exception {
		var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (token != null) {
			req.header("Authorization", "Bearer " + token);
		}
		return mvc.perform(req);
	}

	protected ResultActions getAuth(String path, String token) throws Exception {
		return mvc.perform(get(path).header("Authorization", "Bearer " + token));
	}

	/** Registers a fresh user and returns the JWT. */
	protected String registerToken(String name) throws Exception {
		return registerToken(name, uniqueEmail());
	}

	protected String registerToken(String name, String email) throws Exception {
		ResultActions res = postJson("/api/auth/register", null, registerBody(name, email, PASSWORD))
				.andExpect(status().isCreated());
		return json(res).get("token").asString();
	}

	protected static String registerBody(String name, String email, String password) {
		return """
				{"name":"%s","email":"%s","password":"%s"}""".formatted(name, email, password);
	}

	/** Creates a community owned by the token's user and returns the response JSON. */
	protected JsonNode createCommunity(String token, String name) throws Exception {
		return json(postJson("/api/communities", token, """
				{"name":"%s"}""".formatted(name)).andExpect(status().isCreated()));
	}

}
