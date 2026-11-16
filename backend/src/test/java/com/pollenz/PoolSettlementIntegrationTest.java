package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import tools.jackson.databind.JsonNode;

/** Order (bill split), deliver, paid and automatic settlement. */
class PoolSettlementIntegrationTest extends IntegrationTestBase {

	private TestUser host;
	private TestUser ravi;
	private TestUser meera;
	private TestUser dev;
	private String outsider;
	private long communityId;
	private long poolId;
	private long milk;
	private long chips;
	private long coke;

	/**
	 * Asha hosts (UPI id set) with Milk; Ravi has Chips and Coke; Meera joined with no items; Dev is in
	 * the community but not the pool; the outsider is in neither. The pool is LOCKED.
	 */
	@BeforeEach
	void setUp() throws Exception {
		host = registerUser("Asha");
		ravi = registerUser("Ravi");
		meera = registerUser("Meera");
		dev = registerUser("Dev");
		outsider = registerToken("Out");
		JsonNode community = createCommunity(host.token(), "Hostel");
		communityId = community.get("id").asLong();
		joinCommunity(ravi.token(), community);
		joinCommunity(meera.token(), community);
		joinCommunity(dev.token(), community);
		setUpi(host.token(), "asha@okbank");

		poolId = createPool(host.token(), communityId, "Snacks").get("id").asLong();
		joinPool(ravi.token(), poolId);
		joinPool(meera.token(), poolId);
		milk = addItem(host.token(), poolId, "Milk", 2, 6800).get("id").asLong();
		chips = addItem(ravi.token(), poolId, "Chips", 3, 6000).get("id").asLong();
		coke = addItem(ravi.token(), poolId, "Coke", 1, 4000).get("id").asLong();
		lockPool(host.token(), poolId);
	}

	private void setUpi(String token, String upiId) throws Exception {
		mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"upiId":"%s"}""".formatted(upiId))).andExpect(status().isOk());
	}

	private String base() {
		return "/api/pools/" + poolId;
	}

	/** Fees 1000 over 3 members: 333 each, the remainder 1 to the host. Coke was unavailable. */
	private JsonNode placeOrder() throws Exception {
		return json(order(host.token(), poolId, orderBody(1000, milk, 7000, chips, 5500, coke, 0))
				.andExpect(status().isOk()));
	}

	// ---- happy path ----

	@Test
	void fullLifecycleFromOrderToSettled() throws Exception {
		Instant orderedAt = Instant.parse("2030-03-01T18:00:00Z");
		clock.freezeAt(orderedAt);
		order(host.token(), poolId, orderBody(1000, milk, 7000, chips, 5500, coke, 0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ORDERED"))
				.andExpect(jsonPath("$.feesPaise").value(1000))
				.andExpect(jsonPath("$.hostUpiId").value("asha@okbank"))
				// Estimates are kept; the final prices sit next to them.
				.andExpect(jsonPath("$.currentAmountPaise").value(16800))
				.andExpect(jsonPath("$.members[0].userId").value(host.id()))
				.andExpect(jsonPath("$.members[0].sharePaise").value(7334))
				.andExpect(jsonPath("$.members[0].paid").value(true))
				.andExpect(jsonPath("$.members[0].paidAt").value("2030-03-01T18:00:00Z"))
				.andExpect(jsonPath("$.members[0].items[0].finalPricePaise").value(7000))
				.andExpect(jsonPath("$.members[1].userId").value(ravi.id()))
				.andExpect(jsonPath("$.members[1].sharePaise").value(5833))
				.andExpect(jsonPath("$.members[1].paid").value(false))
				.andExpect(jsonPath("$.members[1].paidAt").doesNotExist())
				.andExpect(jsonPath("$.members[1].items[0].finalPricePaise").value(5500))
				.andExpect(jsonPath("$.members[1].items[1].finalPricePaise").value(0))
				.andExpect(jsonPath("$.members[2].userId").value(meera.id()))
				.andExpect(jsonPath("$.members[2].sharePaise").value(333))
				.andExpect(jsonPath("$.members[2].paid").value(false));
		JsonNode ordered = poolDetail(host.token(), poolId);
		long shareSum = 0;
		for (JsonNode m : ordered.get("members")) {
			shareSum += m.get("sharePaise").asLong();
		}
		assertThat(shareSum).isEqualTo(7000 + 5500 + 0 + 1000);

		// Ravi pays while ORDERED.
		clock.freezeAt(orderedAt.plusSeconds(60));
		markPaid(host.token(), poolId, memberIdOf(ordered, ravi.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ORDERED"))
				.andExpect(jsonPath("$.members[1].paid").value(true))
				.andExpect(jsonPath("$.members[1].paidAt").value("2030-03-01T18:01:00Z"))
				.andExpect(jsonPath("$.members[2].paid").value(false));

		postAuth(base() + "/deliver", host.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DELIVERED"));

		// Meera pays while DELIVERED: the last unpaid member, so the pool settles.
		markPaid(host.token(), poolId, memberIdOf(ordered, meera.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SETTLED"))
				.andExpect(jsonPath("$.members[2].paid").value(true));

		JsonNode settled = poolDetail(ravi.token(), poolId);
		assertThat(settled.get("status").asString()).isEqualTo("SETTLED");
		for (JsonNode m : settled.get("members")) {
			assertThat(m.get("paid").asBoolean()).isTrue();
			assertThat(isNull(m, "paidAt")).isFalse();
		}
		getAuth("/api/communities/" + communityId + "/pools?status=SETTLED", host.token())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(poolId));

		// SETTLED is final.
		for (String action : new String[] { "lock", "cancel", "deliver" }) {
			postAuth(base() + "/" + action, host.token())
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		}
		order(host.token(), poolId, orderBody(0, milk, 1, chips, 1, coke, 1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
	}

	@Test
	void deliveredBeforeAnyPaymentThenSettles() throws Exception {
		JsonNode ordered = placeOrder();
		postAuth(base() + "/deliver", host.token()).andExpect(status().isOk());
		postAuth(base() + "/deliver", host.token())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		markPaid(host.token(), poolId, memberIdOf(ordered, meera.id()))
				.andExpect(jsonPath("$.status").value("DELIVERED"));
		markPaid(host.token(), poolId, memberIdOf(ordered, ravi.id()))
				.andExpect(jsonPath("$.status").value("SETTLED"));
	}

	@Test
	void orderAcceptsValuesAtTheCap() throws Exception {
		order(host.token(), poolId, orderBody(1_000_000_000L, milk, 1_000_000_000L, chips, 1_000_000_000L,
				coke, 1_000_000_000L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feesPaise").value(1_000_000_000L))
				// 1e9 / 3 = 333333333 rest 1.
				.andExpect(jsonPath("$.members[0].sharePaise").value(1_333_333_334L))
				.andExpect(jsonPath("$.members[1].sharePaise").value(2_333_333_333L))
				.andExpect(jsonPath("$.members[2].sharePaise").value(333_333_333L));
	}

	@Test
	void hostOnlyPoolSettlesAtOrder() throws Exception {
		long solo = createPool(host.token(), communityId, "Solo").get("id").asLong();
		long bread = addItem(host.token(), solo, "Bread", 1, 4500).get("id").asLong();
		lockPool(host.token(), solo);

		order(host.token(), solo, orderBody(99, bread, 4600))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SETTLED"))
				.andExpect(jsonPath("$.feesPaise").value(99))
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].sharePaise").value(4699))
				.andExpect(jsonPath("$.members[0].paid").value(true));

		// A pool with no items at all: an empty list is the complete list.
		long empty = createPool(host.token(), communityId, "Empty").get("id").asLong();
		lockPool(host.token(), empty);
		order(host.token(), empty, orderBody(0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SETTLED"))
				.andExpect(jsonPath("$.members[0].sharePaise").value(0));
	}

	// ---- order errors ----

	@Test
	void orderIsHostOnly() throws Exception {
		String body = orderBody(1000, milk, 7000, chips, 5500, coke, 0);
		for (String token : new String[] { ravi.token(), dev.token() }) {
			order(token, poolId, body)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").value("NOT_HOST"));
		}
		order(outsider, poolId, body)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
		order(host.token(), 999_999_999L, body)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("POOL_NOT_FOUND"));
		postJson(base() + "/order", null, body).andExpect(status().isUnauthorized());
		assertUnordered();
	}

	@Test
	void orderValidatesTheBody() throws Exception {
		String[] bad = {
				orderBody(-1, milk, 7000, chips, 5500, coke, 0),
				orderBody(1_000_000_001L, milk, 7000, chips, 5500, coke, 0),
				orderBody(0, milk, -1, chips, 5500, coke, 0),
				orderBody(0, milk, 1_000_000_001L, chips, 5500, coke, 0),
				"""
				{"items":[]}""",
				"""
				{"feesPaise":0}""",
				"""
				{"feesPaise":0,"items":[null]}""",
				"""
				{"feesPaise":0,"items":[{"finalPricePaise":100}]}""",
				"""
				{"feesPaise":0,"items":[{"itemId":%d}]}""".formatted(milk),
				"""
				{"feesPaise":"lots","items":[]}""",
				"{",
		};
		for (String body : bad) {
			order(host.token(), poolId, body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
		}
		assertUnordered();
	}

	@Test
	void orderMustListEveryItemExactlyOnce() throws Exception {
		long otherPool = createPool(ravi.token(), communityId, "Other").get("id").asLong();
		long foreign = addItem(ravi.token(), otherPool, "Bread", 1, 4500).get("id").asLong();

		expectItemsRejected(orderBody(0, milk, 7000, chips, 5500), "missing item ids [" + coke + "]");
		expectItemsRejected(orderBody(0, milk, 7000, chips, 5500, coke, 0, chips, 5500),
				"duplicate item ids [" + chips + "]");
		expectItemsRejected(orderBody(0, milk, 7000, chips, 5500, coke, 0, foreign, 100),
				"item ids not in this pool [" + foreign + "]");
		expectItemsRejected(orderBody(0, milk, 7000, chips, 5500, coke, 0, 999_999_999L, 100),
				"item ids not in this pool [999999999]");
		expectItemsRejected(orderBody(0), "missing item ids [" + milk + ", " + chips + ", " + coke + "]");
		// Several problems at once are all named.
		JsonNode error = json(order(host.token(), poolId, orderBody(0, milk, 1, milk, 1, foreign, 1))
				.andExpect(status().isBadRequest()));
		assertThat(error.get("message").asString())
				.contains("duplicate item ids [" + milk + "]")
				.contains("item ids not in this pool [" + foreign + "]")
				.contains("missing item ids [" + chips + ", " + coke + "]");
		assertUnordered();
	}

	private void expectItemsRejected(String body, String problem) throws Exception {
		JsonNode error = json(order(host.token(), poolId, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED")));
		assertThat(error.get("message").asString()).contains(problem);
	}

	/** The pool is still LOCKED with no fees, shares or final prices. */
	private void assertUnordered() throws Exception {
		JsonNode detail = poolDetail(host.token(), poolId);
		assertThat(detail.get("status").asString()).isEqualTo("LOCKED");
		assertThat(isNull(detail, "feesPaise")).isTrue();
		for (JsonNode m : detail.get("members")) {
			assertThat(isNull(m, "sharePaise")).isTrue();
			assertThat(m.get("paid").asBoolean()).isFalse();
			for (JsonNode item : m.get("items")) {
				assertThat(isNull(item, "finalPricePaise")).isTrue();
			}
		}
	}

	/** Null in the JSON, or left out of it. */
	private static boolean isNull(JsonNode node, String field) {
		return node.get(field) == null || node.get(field).isNull();
	}

	@Test
	void orderOnlyFromLocked() throws Exception {
		long open = createPool(host.token(), communityId, "Open").get("id").asLong();
		long cancelled = createPool(host.token(), communityId, "Cancelled").get("id").asLong();
		postAuth("/api/pools/" + cancelled + "/cancel", host.token()).andExpect(status().isOk());

		for (long id : new long[] { open, cancelled }) {
			// The state is checked before the item list, so even a wrong list gets 409.
			order(host.token(), id, orderBody(0, 999_999_999L, 1))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		}
		placeOrder();
		order(host.token(), poolId, orderBody(1000, milk, 7000, chips, 5500, coke, 0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		// An ORDERED pool can no longer be cancelled.
		postAuth(base() + "/cancel", host.token())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
	}

	// ---- deliver errors ----

	@Test
	void deliverRules() throws Exception {
		postAuth(base() + "/deliver", host.token())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		placeOrder();
		postAuth(base() + "/deliver", ravi.token())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_HOST"));
		postAuth(base() + "/deliver", outsider)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
		postAuth("/api/pools/999999999/deliver", host.token())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("POOL_NOT_FOUND"));
		assertThat(poolDetail(host.token(), poolId).get("status").asString()).isEqualTo("ORDERED");
	}

	// ---- paid errors ----

	@Test
	void paidRules() throws Exception {
		JsonNode before = poolDetail(host.token(), poolId);
		long raviMember = memberIdOf(before, ravi.id());
		long meeraMember = memberIdOf(before, meera.id());
		long hostMember = memberIdOf(before, host.id());
		long otherPool = createPool(dev.token(), communityId, "Other").get("id").asLong();
		long devMember = memberIdOf(poolDetail(dev.token(), otherPool), dev.id());

		// Not ORDERED or DELIVERED yet.
		markPaid(host.token(), poolId, raviMember)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));

		placeOrder();
		markPaid(ravi.token(), poolId, raviMember)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_HOST"));
		markPaid(outsider, poolId, raviMember)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
		markPaid(host.token(), 999_999_999L, raviMember)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("POOL_NOT_FOUND"));
		// A member of another pool, and an id that does not exist.
		for (long id : new long[] { devMember, 999_999_999L }) {
			markPaid(host.token(), poolId, id)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error").value("MEMBER_NOT_FOUND"));
		}
		postAuth(base() + "/members/abc/paid", host.token())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
		// The host paid the app directly at order time.
		markPaid(host.token(), poolId, hostMember)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("ALREADY_PAID"));

		markPaid(host.token(), poolId, raviMember).andExpect(status().isOk());
		markPaid(host.token(), poolId, raviMember)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("ALREADY_PAID"));
		assertThat(poolDetail(host.token(), poolId).get("status").asString()).isEqualTo("ORDERED");

		markPaid(host.token(), poolId, meeraMember).andExpect(jsonPath("$.status").value("SETTLED"));
		// Once SETTLED, payments are closed: the state error comes first.
		markPaid(host.token(), poolId, meeraMember)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
	}

	@Test
	void paidIsRejectedOnCancelledPool() throws Exception {
		long raviMember = memberIdOf(poolDetail(host.token(), poolId), ravi.id());
		postAuth(base() + "/cancel", host.token()).andExpect(status().isOk());
		markPaid(host.token(), poolId, raviMember)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
	}

	// ---- hostUpiId visibility ----

	@Test
	void hostUpiIdIsShownOnlyToPoolMembers() throws Exception {
		for (String token : new String[] { host.token(), ravi.token(), meera.token() }) {
			getAuth(base(), token).andExpect(jsonPath("$.hostUpiId").value("asha@okbank"));
		}
		getAuth(base(), dev.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hostName").value("Asha"))
				.andExpect(jsonPath("$.hostUpiId").doesNotExist());

		// A fresh pool: Dev does not see it before joining, and does in the join response.
		long other = createPool(host.token(), communityId, "Other").get("id").asLong();
		getAuth("/api/pools/" + other, dev.token()).andExpect(jsonPath("$.hostUpiId").doesNotExist());
		postAuth("/api/pools/" + other + "/join", dev.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hostUpiId").value("asha@okbank"));
		getAuth("/api/pools/" + other, dev.token()).andExpect(jsonPath("$.hostUpiId").value("asha@okbank"));
		// After leaving, hidden again.
		deleteAuth("/api/pools/" + other + "/leave", dev.token()).andExpect(status().isNoContent());
		getAuth("/api/pools/" + other, dev.token()).andExpect(jsonPath("$.hostUpiId").doesNotExist());
	}

	@Test
	void hostWithoutUpiIdShowsNull() throws Exception {
		long ravisPool = createPool(ravi.token(), communityId, "Ravi's").get("id").asLong();
		joinPool(meera.token(), ravisPool);
		getAuth("/api/pools/" + ravisPool, meera.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hostUpiId").doesNotExist());
	}

}
