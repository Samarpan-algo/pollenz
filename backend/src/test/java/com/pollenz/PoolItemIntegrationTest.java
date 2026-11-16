package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class PoolItemIntegrationTest extends IntegrationTestBase {

	private String host;
	private String ravi;
	private String meera;
	private String outsider;
	private long communityId;
	private long poolId;

	/** Fresh users, community and pool per test: Asha hosts, Ravi joined, Meera is only in the community. */
	@BeforeEach
	void setUp() throws Exception {
		host = registerToken("Asha");
		ravi = registerToken("Ravi");
		meera = registerToken("Meera");
		outsider = registerToken("Dev");
		JsonNode community = createCommunity(host, "Hostel");
		joinCommunity(ravi, community);
		joinCommunity(meera, community);
		communityId = community.get("id").asLong();
		poolId = createPool(host, communityId, "Snacks").get("id").asLong();
		joinPool(ravi, poolId);
	}

	private String items() {
		return "/api/pools/" + poolId + "/items";
	}

	@Test
	void memberAddsItem() throws Exception {
		postJson(items(), ravi, itemBody("  Chips  ", 3, 6000))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("Chips"))
				.andExpect(jsonPath("$.quantity").value(3))
				.andExpect(jsonPath("$.estPricePaise").value(6000))
				.andExpect(jsonPath("$.finalPricePaise").doesNotExist());
		addItem(host, poolId, "Milk", 1, 3400);

		JsonNode detail = poolDetail(ravi, poolId);
		assertThat(detail.get("currentAmountPaise").asLong()).isEqualTo(9400);
		assertThat(detail.get("members").get(1).get("items").get(0).get("name").asString()).isEqualTo("Chips");
	}

	@Test
	void onlyPoolMembersAddItems() throws Exception {
		postJson(items(), meera, itemBody("Chips", 1, 100))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_IN_POOL"));
		postJson(items(), outsider, itemBody("Chips", 1, 100))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
		postJson("/api/pools/999999999/items", ravi, itemBody("Chips", 1, 100))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("POOL_NOT_FOUND"));
	}

	@Test
	void itemFieldsAreValidated() throws Exception {
		long itemId = addItem(ravi, poolId, "Chips", 1, 100).get("id").asLong();
		String[] bad = {
				itemBody("", 1, 100),
				itemBody("   ", 1, 100),
				itemBody("x".repeat(201), 1, 100),
				itemBody("Chips", 0, 100),
				itemBody("Chips", -1, 100),
				itemBody("Chips", 1, 0),
				itemBody("Chips", 1, -100),
				itemBody("Chips", 1, 1_000_000_001L),
				"""
				{"name":"Chips","quantity":1}""",
				"""
				{"name":"Chips","estPricePaise":100}""",
				"{}",
		};
		for (String body : bad) {
			postJson(items(), ravi, body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
			putJson(items() + "/" + itemId, ravi, body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
		}
		postJson(items(), ravi, itemBody("x".repeat(200), 1, 1_000_000_000L)).andExpect(status().isCreated());
	}

	@Test
	void memberEditsAndDeletesOwnItem() throws Exception {
		long itemId = addItem(ravi, poolId, "Chips", 1, 2000).get("id").asLong();

		putJson(items() + "/" + itemId, ravi, itemBody("Big chips", 2, 5000))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(itemId))
				.andExpect(jsonPath("$.name").value("Big chips"))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.estPricePaise").value(5000));
		JsonNode detail = poolDetail(ravi, poolId);
		assertThat(detail.get("currentAmountPaise").asLong()).isEqualTo(5000);
		assertThat(detail.get("members").get(1).get("items").get(0).get("name").asString()).isEqualTo("Big chips");

		deleteAuth(items() + "/" + itemId, ravi).andExpect(status().isNoContent());
		detail = poolDetail(ravi, poolId);
		assertThat(detail.get("currentAmountPaise").asLong()).isZero();
		assertThat(detail.get("members").get(1).get("items").size()).isZero();
		deleteAuth(items() + "/" + itemId, ravi)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("ITEM_NOT_FOUND"));
	}

	@Test
	void cannotTouchSomeoneElsesItem() throws Exception {
		long hostItem = addItem(host, poolId, "Milk", 1, 3400).get("id").asLong();
		long raviItem = addItem(ravi, poolId, "Chips", 1, 2000).get("id").asLong();

		putJson(items() + "/" + hostItem, ravi, itemBody("Mine now", 1, 1))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_YOUR_ITEM"));
		deleteAuth(items() + "/" + hostItem, ravi)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_YOUR_ITEM"));
		// The host has no special rights over members' items either.
		deleteAuth(items() + "/" + raviItem, host)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_YOUR_ITEM"));
		// Not in the pool at all.
		putJson(items() + "/" + raviItem, meera, itemBody("x", 1, 1))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_IN_POOL"));
		deleteAuth(items() + "/" + raviItem, outsider)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));

		assertThat(poolDetail(host, poolId).get("currentAmountPaise").asLong()).isEqualTo(5400);
	}

	@Test
	void itemOfAnotherPoolIsNotFound() throws Exception {
		long otherPool = createPool(ravi, communityId, "Other").get("id").asLong();
		long otherItem = addItem(ravi, otherPool, "Bread", 1, 4500).get("id").asLong();

		putJson(items() + "/" + otherItem, ravi, itemBody("x", 1, 1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("ITEM_NOT_FOUND"));
		deleteAuth(items() + "/" + otherItem, ravi)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("ITEM_NOT_FOUND"));
		deleteAuth(items() + "/999999999", ravi)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("ITEM_NOT_FOUND"));
		assertThat(poolDetail(ravi, otherPool).get("currentAmountPaise").asLong()).isEqualTo(4500);
	}

	@Test
	void noItemChangesUnlessOpen() throws Exception {
		long itemId = addItem(ravi, poolId, "Chips", 1, 2000).get("id").asLong();
		postAuth("/api/pools/" + poolId + "/lock", host).andExpect(status().isOk());

		postJson(items(), ravi, itemBody("Coke", 1, 4000))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
		putJson(items() + "/" + itemId, ravi, itemBody("Coke", 1, 4000))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
		deleteAuth(items() + "/" + itemId, ravi)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));

		postAuth("/api/pools/" + poolId + "/cancel", host).andExpect(status().isOk());
		postJson(items(), host, itemBody("Coke", 1, 4000))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));

		JsonNode detail = poolDetail(ravi, poolId);
		assertThat(detail.get("currentAmountPaise").asLong()).isEqualTo(2000);
	}

	/** Once closes_at has passed, joins and item changes stop even though the job has not run yet. */
	@Test
	void joinAndItemChangesStopAtClosesAtBeforeTheExpiryJob() throws Exception {
		long itemId = addItem(ravi, poolId, "Chips", 1, 2000).get("id").asLong();
		Instant closesAt = Instant.parse(poolDetail(host, poolId).get("closesAt").asString());

		// One second before the deadline everything still works.
		clock.freezeAt(closesAt.minusSeconds(1));
		putJson(items() + "/" + itemId, ravi, itemBody("Chips", 2, 3000)).andExpect(status().isOk());

		// At the deadline (closes_at <= now, the expiry job's own test) and after it: rejected.
		for (Instant now : new Instant[] { closesAt, closesAt.plusSeconds(3600) }) {
			clock.freezeAt(now);
			postAuth("/api/pools/" + poolId + "/join", meera)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
			postJson(items(), ravi, itemBody("Coke", 1, 4000))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
			putJson(items() + "/" + itemId, ravi, itemBody("Coke", 1, 4000))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
			deleteAuth(items() + "/" + itemId, ravi)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
		}
		// Membership and ownership are still checked first.
		postJson(items(), meera, itemBody("Coke", 1, 4000))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("NOT_IN_POOL"));

		JsonNode detail = poolDetail(host, poolId);
		assertThat(detail.get("status").asString()).isEqualTo("OPEN");
		assertThat(detail.get("currentAmountPaise").asLong()).isEqualTo(3000);
		assertThat(detail.get("members").size()).isEqualTo(2);

		// Leaving stays allowed until the status changes.
		deleteAuth("/api/pools/" + poolId + "/leave", ravi).andExpect(status().isNoContent());
		assertThat(poolDetail(host, poolId).get("members").size()).isEqualTo(1);
	}

}
