package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class CommunityIntegrationTest extends IntegrationTestBase {

	private String joinBody(String code) {
		return """
				{"inviteCode":"%s"}""".formatted(code);
	}

	@Test
	void createMakesCreatorAdminWithValidInviteCode() throws Exception {
		String token = registerToken("Asha");
		JsonNode c = createCommunity(token, "Hostel A");
		assertThat(c.get("inviteCode").asString()).matches("[A-Z0-9]{8}");
		assertThat(c.get("role").asString()).isEqualTo("ADMIN");

		getAuth("/api/communities/" + c.get("id").asLong(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Hostel A"))
				.andExpect(jsonPath("$.inviteCode").value(c.get("inviteCode").asString()))
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].name").value("Asha"))
				.andExpect(jsonPath("$.members[0].role").value("ADMIN"))
				.andExpect(jsonPath("$.members[0].userId").isNumber())
				.andExpect(jsonPath("$.members[0].joinedAt").isNotEmpty());
	}

	@Test
	void inviteCodesAreUnique() throws Exception {
		String token = registerToken("Asha");
		String a = createCommunity(token, "One").get("inviteCode").asString();
		String b = createCommunity(token, "Two").get("inviteCode").asString();
		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void joinByCodeAddsMemberAndListsCommunity() throws Exception {
		String admin = registerToken("Asha");
		String member = registerToken("Ravi");
		JsonNode c = createCommunity(admin, "Hostel B");

		postJson("/api/communities/join", member, joinBody(c.get("inviteCode").asString().toLowerCase()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(c.get("id").asLong()))
				.andExpect(jsonPath("$.role").value("MEMBER"));

		getAuth("/api/communities", member)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Hostel B"));

		getAuth("/api/communities/" + c.get("id").asLong(), member)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(2));
	}

	@Test
	void listOnlyShowsMyCommunities() throws Exception {
		String a = registerToken("Asha");
		String b = registerToken("Ravi");
		createCommunity(a, "Only A");
		getAuth("/api/communities", b)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void joinWithUnknownCodeIs404() throws Exception {
		String token = registerToken("Asha");
		postJson("/api/communities/join", token, joinBody("ZZZZZZZZ"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("COMMUNITY_NOT_FOUND"));
	}

	@Test
	void doubleJoinIsConflict() throws Exception {
		String admin = registerToken("Asha");
		String member = registerToken("Ravi");
		String code = createCommunity(admin, "Hostel C").get("inviteCode").asString();

		postJson("/api/communities/join", member, joinBody(code)).andExpect(status().isOk());
		postJson("/api/communities/join", member, joinBody(code))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("ALREADY_MEMBER"));
		// The creator is already an ADMIN member too.
		postJson("/api/communities/join", admin, joinBody(code))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("ALREADY_MEMBER"));
	}

	@Test
	void nonMemberCannotViewCommunity() throws Exception {
		String admin = registerToken("Asha");
		String outsider = registerToken("Ravi");
		long id = createCommunity(admin, "Hostel D").get("id").asLong();
		getAuth("/api/communities/" + id, outsider)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
	}

	@Test
	void missingCommunityIs404() throws Exception {
		String token = registerToken("Asha");
		getAuth("/api/communities/999999999", token)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("COMMUNITY_NOT_FOUND"));
	}

	@Test
	void validationFailuresAre400() throws Exception {
		String token = registerToken("Asha");
		postJson("/api/communities", token, """
				{"name":""}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
		postJson("/api/communities", token, "{}").andExpect(status().isBadRequest());
		postJson("/api/communities/join", token, "{}").andExpect(status().isBadRequest());
		postJson("/api/communities", token, "not json").andExpect(status().isBadRequest());
	}

	@Test
	void communityRoutesRequireAuth() throws Exception {
		postJson("/api/communities", null, """
				{"name":"x"}""").andExpect(status().isUnauthorized());
	}

}
