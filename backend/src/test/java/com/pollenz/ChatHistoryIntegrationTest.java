package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.pollenz.chat.ChatService;

import tools.jackson.databind.JsonNode;

class ChatHistoryIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ChatService chat;

	private record Setup(TestUser host, TestUser member, TestUser outsider, long poolId) {
	}

	/** A pool with a host and one member, plus a community member who is not in the pool. */
	private Setup setup() throws Exception {
		TestUser host = registerUser("Host");
		TestUser member = registerUser("Member");
		TestUser outsider = registerUser("Outsider");
		JsonNode community = createCommunity(host.token(), "Block A");
		joinCommunity(member.token(), community);
		joinCommunity(outsider.token(), community);
		long poolId = createPool(host.token(), community.get("id").asLong(), "Snacks").get("id").asLong();
		joinPool(member.token(), poolId);
		return new Setup(host, member, outsider, poolId);
	}

	@Test
	void membersSeeMessagesOldestFirstWithSenderNames() throws Exception {
		Setup s = setup();
		chat.post(s.host().id(), s.poolId(), "first");
		chat.post(s.member().id(), s.poolId(), "second");
		chat.post(s.host().id(), s.poolId(), "third");

		JsonNode list = json(getAuth("/api/pools/" + s.poolId() + "/messages", s.member().token())
				.andExpect(status().isOk()));
		assertThat(list.size()).isEqualTo(3);
		assertThat(list.get(0).get("body").asString()).isEqualTo("first");
		assertThat(list.get(1).get("body").asString()).isEqualTo("second");
		assertThat(list.get(2).get("body").asString()).isEqualTo("third");
		assertThat(list.get(1).get("senderName").asString()).isEqualTo("Member");
		assertThat(list.get(1).get("senderId").asLong()).isEqualTo(s.member().id());
		assertThat(list.get(1).get("poolId").asLong()).isEqualTo(s.poolId());
		assertThat(list.get(1).get("createdAt").asString()).isNotBlank();
		assertThat(list.get(0).get("id").asLong()).isLessThan(list.get(2).get("id").asLong());
	}

	@Test
	void emptyHistoryIsEmptyList() throws Exception {
		Setup s = setup();
		assertThat(json(getAuth("/api/pools/" + s.poolId() + "/messages", s.host().token())).size()).isZero();
	}

	@Test
	void historyIsLimitedToTheLatest200() throws Exception {
		Setup s = setup();
		for (int i = 1; i <= 205; i++) {
			chat.post(s.host().id(), s.poolId(), "m" + i);
		}
		JsonNode list = json(getAuth("/api/pools/" + s.poolId() + "/messages", s.host().token())
				.andExpect(status().isOk()));
		assertThat(list.size()).isEqualTo(200);
		assertThat(list.get(0).get("body").asString()).isEqualTo("m6");
		assertThat(list.get(199).get("body").asString()).isEqualTo("m205");
	}

	@Test
	void historyOfOtherPoolsIsNotMixedIn() throws Exception {
		Setup s = setup();
		JsonNode community = createCommunity(s.host().token(), "Block B");
		long otherPool = createPool(s.host().token(), community.get("id").asLong(), "Other").get("id").asLong();
		chat.post(s.host().id(), otherPool, "elsewhere");
		chat.post(s.host().id(), s.poolId(), "here");

		JsonNode list = json(getAuth("/api/pools/" + s.poolId() + "/messages", s.host().token()));
		assertThat(list.size()).isEqualTo(1);
		assertThat(list.get(0).get("body").asString()).isEqualTo("here");
	}

	@Test
	void nonPoolMemberGetsNotInPool() throws Exception {
		Setup s = setup();
		var res = getAuth("/api/pools/" + s.poolId() + "/messages", s.outsider().token());
		assertThat(statusOf(res)).isEqualTo(403);
		assertThat(errorOf(res)).isEqualTo("NOT_IN_POOL");
	}

	@Test
	void outsiderOfTheCommunityIsForbidden() throws Exception {
		Setup s = setup();
		TestUser stranger = registerUser("Stranger");
		var res = getAuth("/api/pools/" + s.poolId() + "/messages", stranger.token());
		assertThat(statusOf(res)).isEqualTo(403);
		assertThat(errorOf(res)).isEqualTo("NOT_A_MEMBER");
	}

	@Test
	void unknownPoolIs404() throws Exception {
		TestUser user = registerUser("Solo");
		var res = getAuth("/api/pools/999999999/messages", user.token());
		assertThat(statusOf(res)).isEqualTo(404);
		assertThat(errorOf(res)).isEqualTo("POOL_NOT_FOUND");
	}

	@Test
	void unauthenticatedIs401() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/pools/1/messages"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void postValidatesTrimmedBodyLength() throws Exception {
		Setup s = setup();
		org.junit.jupiter.api.Assertions.assertThrows(com.pollenz.common.ApiException.class,
				() -> chat.post(s.host().id(), s.poolId(), "   "));
		org.junit.jupiter.api.Assertions.assertThrows(com.pollenz.common.ApiException.class,
				() -> chat.post(s.host().id(), s.poolId(), "x".repeat(1001)));
		org.junit.jupiter.api.Assertions.assertThrows(com.pollenz.common.ApiException.class,
				() -> chat.post(s.outsider().id(), s.poolId(), "hello"));
		assertThat(chat.post(s.host().id(), s.poolId(), "  " + "y".repeat(1000) + "  ").body()).hasSize(1000);
	}

}
