package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import tools.jackson.databind.JsonNode;

/** Pool create, list, detail, join, leave, lock and cancel. Items: PoolItemIntegrationTest. */
class PoolIntegrationTest extends IntegrationTestBase {

	private static final Instant TOMORROW = Instant.now().plus(Duration.ofDays(1));

	private String poolsPath(long communityId) {
		return "/api/communities/" + communityId + "/pools";
	}

	// ---- create ----

	@Test
	void createStartsOpenWithHostAsOnlyMember() throws Exception {
		TestUser host = registerUser("Asha");
		long communityId = createCommunity(host.token(), "Hostel A").get("id").asLong();
		Instant closesAt = Instant.parse("2099-01-01T10:00:00Z");

		postJson(poolsPath(communityId), host.token(),
				poolBody("ZEPTO", "  Late night snacks  ", 19900, closesAt, " Gate 2 "))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.communityId").value(communityId))
				.andExpect(jsonPath("$.platform").value("ZEPTO"))
				.andExpect(jsonPath("$.title").value("Late night snacks"))
				.andExpect(jsonPath("$.pickupSpot").value("Gate 2"))
				.andExpect(jsonPath("$.targetAmountPaise").value(19900))
				.andExpect(jsonPath("$.currentAmountPaise").value(0))
				.andExpect(jsonPath("$.closesAt").value("2099-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.feesPaise").doesNotExist())
				.andExpect(jsonPath("$.hostId").value(host.id()))
				.andExpect(jsonPath("$.hostName").value("Asha"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].userId").value(host.id()))
				.andExpect(jsonPath("$.members[0].memberId").isNumber())
				.andExpect(jsonPath("$.members[0].items.length()").value(0));
	}

	@Test
	void everyPlatformIsAccepted() throws Exception {
		String host = registerToken("Asha");
		long communityId = createCommunity(host, "Hostel").get("id").asLong();
		for (String p : new String[] { "BLINKIT", "INSTAMART", "ZEPTO", "BIGBASKET", "OTHER" }) {
			postJson(poolsPath(communityId), host, poolBody(p, "t", 100, TOMORROW, "s"))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.platform").value(p));
		}
	}

	@Test
	void createRequiresCommunityMembership() throws Exception {
		String admin = registerToken("Asha");
		String outsider = registerToken("Ravi");
		long communityId = createCommunity(admin, "Hostel").get("id").asLong();

		postJson(poolsPath(communityId), outsider, poolBody("x"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
		postJson(poolsPath(999_999_999L), admin, poolBody("x"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("COMMUNITY_NOT_FOUND"));
		postJson(poolsPath(communityId), null, poolBody("x")).andExpect(status().isUnauthorized());
	}

	@Test
	void createValidatesFields() throws Exception {
		String host = registerToken("Asha");
		String path = poolsPath(createCommunity(host, "Hostel").get("id").asLong());
		String longText = "x".repeat(201);
		Instant past = Instant.now().minus(Duration.ofMinutes(1));

		String[] bad = {
				poolBody("SWIGGY", "t", 100, TOMORROW, "s"),
				poolBody("blinkit", "t", 100, TOMORROW, "s"),
				poolBody("BLINKIT", "", 100, TOMORROW, "s"),
				poolBody("BLINKIT", "   ", 100, TOMORROW, "s"),
				poolBody("BLINKIT", longText, 100, TOMORROW, "s"),
				poolBody("BLINKIT", "t", 0, TOMORROW, "s"),
				poolBody("BLINKIT", "t", -5, TOMORROW, "s"),
				poolBody("BLINKIT", "t", 1_000_000_001L, TOMORROW, "s"),
				poolBody("BLINKIT", "t", 100, past, "s"),
				poolBody("BLINKIT", "t", 100, TOMORROW, ""),
				poolBody("BLINKIT", "t", 100, TOMORROW, longText),
				"""
				{"platform":"BLINKIT","title":"t","targetAmountPaise":100,"closesAt":"not-a-date","pickupSpot":"s"}""",
				"""
				{"title":"t","targetAmountPaise":100,"pickupSpot":"s"}""",
				"{}",
		};
		for (String body : bad) {
			postJson(path, host, body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
		}
		// Boundaries that are valid.
		postJson(path, host, poolBody("OTHER", "x".repeat(200), 1, TOMORROW, "y".repeat(200)))
				.andExpect(status().isCreated());
	}

	// ---- list ----

	@Test
	void listHasAggregatesAndIsNewestFirst() throws Exception {
		TestUser host = registerUser("Asha");
		TestUser ravi = registerUser("Ravi");
		JsonNode community = createCommunity(host.token(), "Hostel");
		long communityId = community.get("id").asLong();
		joinCommunity(ravi.token(), community);

		long older = createPool(host.token(), communityId, "Older").get("id").asLong();
		joinPool(ravi.token(), older);
		addItem(host.token(), older, "Milk", 2, 6800);
		addItem(ravi.token(), older, "Chips", 3, 6000);
		addItem(ravi.token(), older, "Coke", 1, 4000);
		long newer = createPool(ravi.token(), communityId, "Newer").get("id").asLong();

		getAuth(poolsPath(communityId), ravi.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(newer))
				.andExpect(jsonPath("$[0].title").value("Newer"))
				.andExpect(jsonPath("$[0].currentAmountPaise").value(0))
				.andExpect(jsonPath("$[0].memberCount").value(1))
				.andExpect(jsonPath("$[0].hostId").value(ravi.id()))
				.andExpect(jsonPath("$[0].hostName").value("Ravi"))
				.andExpect(jsonPath("$[1].id").value(older))
				.andExpect(jsonPath("$[1].currentAmountPaise").value(16800))
				.andExpect(jsonPath("$[1].memberCount").value(2))
				.andExpect(jsonPath("$[1].hostName").value("Asha"))
				.andExpect(jsonPath("$[1].targetAmountPaise").value(19900))
				.andExpect(jsonPath("$[1].platform").value("BLINKIT"))
				.andExpect(jsonPath("$[1].status").value("OPEN"))
				.andExpect(jsonPath("$[1].pickupSpot").value("Gate 2"))
				.andExpect(jsonPath("$[1].closesAt").isNotEmpty())
				.andExpect(jsonPath("$[1].createdAt").isNotEmpty())
				.andExpect(jsonPath("$[1].communityId").value(communityId));
	}

	@Test
	void listFiltersByStatus() throws Exception {
		String host = registerToken("Asha");
		long communityId = createCommunity(host, "Hostel").get("id").asLong();
		long open = createPool(host, communityId, "Open").get("id").asLong();
		long locked = createPool(host, communityId, "Locked").get("id").asLong();
		long cancelled = createPool(host, communityId, "Cancelled").get("id").asLong();
		postAuth("/api/pools/" + locked + "/lock", host).andExpect(status().isOk());
		postAuth("/api/pools/" + cancelled + "/cancel", host).andExpect(status().isOk());

		getAuth(poolsPath(communityId) + "?status=OPEN", host)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(open));
		getAuth(poolsPath(communityId) + "?status=LOCKED", host)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(locked))
				.andExpect(jsonPath("$[0].status").value("LOCKED"));
		getAuth(poolsPath(communityId) + "?status=CANCELLED", host)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(cancelled));
		getAuth(poolsPath(communityId) + "?status=SETTLED", host)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		getAuth(poolsPath(communityId), host).andExpect(jsonPath("$.length()").value(3));
		getAuth(poolsPath(communityId) + "?status=NOPE", host)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
	}

	@Test
	void listShowsOnlyThatCommunityAndOnlyToMembers() throws Exception {
		String a = registerToken("Asha");
		String b = registerToken("Ravi");
		long communityA = createCommunity(a, "A").get("id").asLong();
		long communityB = createCommunity(b, "B").get("id").asLong();
		createPool(a, communityA, "In A");
		createPool(b, communityB, "In B");

		getAuth(poolsPath(communityA), a)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("In A"));
		getAuth(poolsPath(communityA), b)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
		getAuth(poolsPath(999_999_999L), a)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("COMMUNITY_NOT_FOUND"));
	}

	// ---- detail ----

	@Test
	void detailShowsMembersItemsSharesAndHostUpi() throws Exception {
		TestUser host = registerUser("Asha");
		TestUser ravi = registerUser("Ravi");
		TestUser meera = registerUser("Meera");
		JsonNode community = createCommunity(host.token(), "Hostel");
		joinCommunity(ravi.token(), community);
		joinCommunity(meera.token(), community);
		mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + host.token())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"upiId":"asha@okbank"}""")).andExpect(status().isOk());

		long poolId = createPool(host.token(), community.get("id").asLong(), "Snacks").get("id").asLong();
		joinPool(ravi.token(), poolId);
		long chips = addItem(ravi.token(), poolId, "Chips", 3, 6000).get("id").asLong();
		addItem(ravi.token(), poolId, "Coke", 1, 4000);
		addItem(host.token(), poolId, "Milk", 2, 6800);

		// Meera is a community member but not in the pool: she can still view it.
		getAuth("/api/pools/" + poolId, meera.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(poolId))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.currentAmountPaise").value(16800))
				.andExpect(jsonPath("$.hostId").value(host.id()))
				.andExpect(jsonPath("$.hostName").value("Asha"))
				.andExpect(jsonPath("$.hostUpiId").value("asha@okbank"))
				.andExpect(jsonPath("$.members.length()").value(2))
				.andExpect(jsonPath("$.members[0].userId").value(host.id()))
				.andExpect(jsonPath("$.members[0].name").value("Asha"))
				.andExpect(jsonPath("$.members[0].items.length()").value(1))
				.andExpect(jsonPath("$.members[0].items[0].name").value("Milk"))
				.andExpect(jsonPath("$.members[0].sharePaise").doesNotExist())
				.andExpect(jsonPath("$.members[0].paid").value(false))
				.andExpect(jsonPath("$.members[1].userId").value(ravi.id()))
				.andExpect(jsonPath("$.members[1].name").value("Ravi"))
				.andExpect(jsonPath("$.members[1].paid").value(false))
				.andExpect(jsonPath("$.members[1].sharePaise").doesNotExist())
				.andExpect(jsonPath("$.members[1].items.length()").value(2))
				.andExpect(jsonPath("$.members[1].items[0].id").value(chips))
				.andExpect(jsonPath("$.members[1].items[0].name").value("Chips"))
				.andExpect(jsonPath("$.members[1].items[0].quantity").value(3))
				.andExpect(jsonPath("$.members[1].items[0].estPricePaise").value(6000))
				.andExpect(jsonPath("$.members[1].items[0].finalPricePaise").doesNotExist())
				.andExpect(jsonPath("$.members[1].items[1].name").value("Coke"));
	}

	@Test
	void detailIs404ForMissingPoolAnd403ForOutsiders() throws Exception {
		String host = registerToken("Asha");
		String outsider = registerToken("Ravi");
		long poolId = createPool(host, createCommunity(host, "Hostel").get("id").asLong(), "P").get("id").asLong();

		getAuth("/api/pools/999999999", host)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("POOL_NOT_FOUND"));
		getAuth("/api/pools/" + poolId, outsider)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
	}

	// ---- join ----

	@Test
	void communityMemberCanJoinOnce() throws Exception {
		TestUser host = registerUser("Asha");
		TestUser ravi = registerUser("Ravi");
		JsonNode community = createCommunity(host.token(), "Hostel");
		joinCommunity(ravi.token(), community);
		long poolId = createPool(host.token(), community.get("id").asLong(), "P").get("id").asLong();

		postAuth("/api/pools/" + poolId + "/join", ravi.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(2))
				.andExpect(jsonPath("$.members[1].userId").value(ravi.id()));
		postAuth("/api/pools/" + poolId + "/join", ravi.token())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("ALREADY_IN_POOL"));
		postAuth("/api/pools/" + poolId + "/join", host.token())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("ALREADY_IN_POOL"));
	}

	@Test
	void joinRejectsOutsidersMissingPoolsAndClosedPools() throws Exception {
		String host = registerToken("Asha");
		String ravi = registerToken("Ravi");
		String outsider = registerToken("Meera");
		JsonNode community = createCommunity(host, "Hostel");
		joinCommunity(ravi, community);
		long communityId = community.get("id").asLong();
		long locked = createPool(host, communityId, "Locked").get("id").asLong();
		long cancelled = createPool(host, communityId, "Cancelled").get("id").asLong();
		postAuth("/api/pools/" + locked + "/lock", host).andExpect(status().isOk());
		postAuth("/api/pools/" + cancelled + "/cancel", host).andExpect(status().isOk());
		long open = createPool(host, communityId, "Open").get("id").asLong();

		postAuth("/api/pools/" + open + "/join", outsider)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
		postAuth("/api/pools/999999999/join", ravi)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("POOL_NOT_FOUND"));
		for (long closed : new long[] { locked, cancelled }) {
			postAuth("/api/pools/" + closed + "/join", ravi)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
		}
	}

	// ---- leave ----

	@Test
	void leaveRemovesMemberAndTheirItems() throws Exception {
		TestUser host = registerUser("Asha");
		TestUser ravi = registerUser("Ravi");
		JsonNode community = createCommunity(host.token(), "Hostel");
		joinCommunity(ravi.token(), community);
		long communityId = community.get("id").asLong();
		long poolId = createPool(host.token(), communityId, "P").get("id").asLong();
		joinPool(ravi.token(), poolId);
		addItem(ravi.token(), poolId, "Chips", 3, 6000);
		addItem(host.token(), poolId, "Milk", 1, 3400);

		deleteAuth("/api/pools/" + poolId + "/leave", ravi.token()).andExpect(status().isNoContent());

		JsonNode detail = poolDetail(host.token(), poolId);
		assertThat(detail.get("members").size()).isEqualTo(1);
		assertThat(detail.get("currentAmountPaise").asLong()).isEqualTo(3400);
		getAuth(poolsPath(communityId), host.token())
				.andExpect(jsonPath("$[0].memberCount").value(1))
				.andExpect(jsonPath("$[0].currentAmountPaise").value(3400));

		// Leaving twice: no longer in the pool. Joining again works and starts with no items.
		deleteAuth("/api/pools/" + poolId + "/leave", ravi.token())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_IN_POOL"));
		postAuth("/api/pools/" + poolId + "/join", ravi.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members[1].items.length()").value(0));
	}

	@Test
	void leaveRules() throws Exception {
		String host = registerToken("Asha");
		String ravi = registerToken("Ravi");
		String meera = registerToken("Meera");
		String outsider = registerToken("Dev");
		JsonNode community = createCommunity(host, "Hostel");
		joinCommunity(ravi, community);
		joinCommunity(meera, community);
		long poolId = createPool(host, community.get("id").asLong(), "P").get("id").asLong();
		joinPool(ravi, poolId);

		deleteAuth("/api/pools/" + poolId + "/leave", host)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("HOST_CANNOT_LEAVE"));
		deleteAuth("/api/pools/" + poolId + "/leave", meera)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_IN_POOL"));
		deleteAuth("/api/pools/" + poolId + "/leave", outsider)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
		deleteAuth("/api/pools/999999999/leave", ravi)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("POOL_NOT_FOUND"));

		postAuth("/api/pools/" + poolId + "/lock", host).andExpect(status().isOk());
		deleteAuth("/api/pools/" + poolId + "/leave", ravi)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
		assertThat(poolDetail(host, poolId).get("members").size()).isEqualTo(2);
	}

	// ---- lock and cancel ----

	@Test
	void hostLocksThenCancels() throws Exception {
		String host = registerToken("Asha");
		long poolId = createPool(host, createCommunity(host, "Hostel").get("id").asLong(), "P").get("id").asLong();
		String base = "/api/pools/" + poolId;

		postAuth(base + "/lock", host)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("LOCKED"));
		postAuth(base + "/lock", host)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		postAuth(base + "/cancel", host)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		postAuth(base + "/cancel", host)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		postAuth(base + "/lock", host)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		assertThat(poolDetail(host, poolId).get("status").asString()).isEqualTo("CANCELLED");
	}

	@Test
	void hostCancelsOpenPool() throws Exception {
		String host = registerToken("Asha");
		long poolId = createPool(host, createCommunity(host, "Hostel").get("id").asLong(), "P").get("id").asLong();
		postAuth("/api/pools/" + poolId + "/cancel", host)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void onlyHostCanLockOrCancel() throws Exception {
		String host = registerToken("Asha");
		String ravi = registerToken("Ravi");
		String outsider = registerToken("Meera");
		JsonNode community = createCommunity(host, "Hostel");
		joinCommunity(ravi, community);
		long poolId = createPool(host, community.get("id").asLong(), "P").get("id").asLong();
		joinPool(ravi, poolId);

		for (String action : new String[] { "lock", "cancel" }) {
			postAuth("/api/pools/" + poolId + "/" + action, ravi)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").value("NOT_HOST"));
			postAuth("/api/pools/" + poolId + "/" + action, outsider)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
			postAuth("/api/pools/999999999/" + action, host)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error").value("POOL_NOT_FOUND"));
		}
		assertThat(poolDetail(host, poolId).get("status").asString()).isEqualTo("OPEN");
	}

	@Test
	void poolRoutesRequireAuth() throws Exception {
		mvc.perform(get("/api/pools/1")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/pools/1/join")).andExpect(status().isUnauthorized());
	}

}
