package com.pollenz;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class UserIntegrationTest extends IntegrationTestBase {

	private ResultActions patchMe(String token, String body) throws Exception {
		return mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void getMeReturnsProfile() throws Exception {
		String email = uniqueEmail();
		String token = registerToken("Asha", email);
		getAuth("/api/users/me", token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Asha"))
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.upiId").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void patchUpdatesOnlyPresentFields() throws Exception {
		String token = registerToken("Asha");
		patchMe(token, """
				{"upiId":"asha@okbank"}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upiId").value("asha@okbank"))
				.andExpect(jsonPath("$.name").value("Asha"));
		patchMe(token, """
				{"name":"Asha K"}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Asha K"))
				.andExpect(jsonPath("$.upiId").value("asha@okbank"));
		getAuth("/api/users/me", token)
				.andExpect(jsonPath("$.name").value("Asha K"))
				.andExpect(jsonPath("$.upiId").value("asha@okbank"));
	}

	@Test
	void emptyUpiIdClearsIt() throws Exception {
		String token = registerToken("Asha");
		patchMe(token, """
				{"upiId":"asha@okbank"}""").andExpect(status().isOk());
		patchMe(token, """
				{"upiId":""}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upiId").doesNotExist());
	}

	@Test
	void invalidPatchIs400() throws Exception {
		String token = registerToken("Asha");
		patchMe(token, """
				{"upiId":"no-at-sign"}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
		patchMe(token, """
				{"name":"   "}""").andExpect(status().isBadRequest());
	}

	@Test
	void patchWithoutTokenIs401() throws Exception {
		mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

}
