package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;

/**
 * Fires requests at the same pool at the same moment and checks that the final state is one that
 * some serial order of the successful requests would produce.
 */
class PoolConcurrencyIntegrationTest extends IntegrationTestBase {

	private static final int ROUNDS = 3;

	private String host;
	private JsonNode community;
	private long communityId;

	private void setUpCommunity() throws Exception {
		host = registerToken("Asha");
		community = createCommunity(host, "Hostel");
		communityId = community.get("id").asLong();
	}

	/** n new users, each already a member of the community. */
	private List<String> communityMembers(int n) throws Exception {
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			String token = registerToken("User " + i);
			joinCommunity(token, community);
			tokens.add(token);
		}
		return tokens;
	}

	private static long count(List<ResultActions> results, int status) {
		return results.stream().filter(r -> statusOf(r) == status).count();
	}

	@Test
	void manySimultaneousJoinsAllLand() throws Exception {
		setUpCommunity();
		List<String> users = communityMembers(16);
		long poolId = createPool(host, communityId, "P").get("id").asLong();

		List<Callable<ResultActions>> calls = new ArrayList<>();
		for (String u : users) {
			calls.add(() -> postAuth("/api/pools/" + poolId + "/join", u));
		}
		List<ResultActions> results = concurrently(calls);

		assertThat(count(results, 200)).isEqualTo(users.size());
		assertThat(poolDetail(host, poolId).get("members").size()).isEqualTo(users.size() + 1);
		JsonNode card = json(getAuth("/api/communities/" + communityId + "/pools", host)).get(0);
		assertThat(card.get("memberCount").asLong()).isEqualTo(users.size() + 1);
	}

	@Test
	void sameUserJoiningManyTimesAtOnceJoinsOnce() throws Exception {
		setUpCommunity();
		String ravi = communityMembers(1).get(0);
		long poolId = createPool(host, communityId, "P").get("id").asLong();

		List<Callable<ResultActions>> calls = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			calls.add(() -> postAuth("/api/pools/" + poolId + "/join", ravi));
		}
		List<ResultActions> results = concurrently(calls);

		assertThat(count(results, 200)).isEqualTo(1);
		for (ResultActions r : results) {
			if (statusOf(r) != 200) {
				assertThat(statusOf(r)).isEqualTo(409);
				assertThat(errorOf(r)).isEqualTo("ALREADY_IN_POOL");
			}
		}
		assertThat(poolDetail(host, poolId).get("members").size()).isEqualTo(2);
	}

	/**
	 * Joins racing the host's lock. The lock response lists the members at the instant of locking;
	 * no join may land after that, so the final member list must equal it.
	 */
	@Test
	void joinsRacingLockNeverLandAfterTheLock() throws Exception {
		setUpCommunity();
		List<String> users = communityMembers(8);
		for (int round = 0; round < ROUNDS; round++) {
			long poolId = createPool(host, communityId, "Round " + round).get("id").asLong();

			List<Callable<ResultActions>> calls = new ArrayList<>();
			calls.add(() -> postAuth("/api/pools/" + poolId + "/lock", host));
			for (String u : users) {
				calls.add(() -> postAuth("/api/pools/" + poolId + "/join", u));
			}
			List<ResultActions> results = concurrently(calls);
			ResultActions lock = results.get(0);
			List<ResultActions> joins = results.subList(1, results.size());

			assertThat(statusOf(lock)).isEqualTo(200);
			long joined = count(joins, 200);
			for (ResultActions j : joins) {
				if (statusOf(j) != 200) {
					assertThat(statusOf(j)).isEqualTo(409);
					assertThat(errorOf(j)).isEqualTo("POOL_NOT_OPEN");
				}
			}
			JsonNode finalState = poolDetail(host, poolId);
			assertThat(finalState.get("status").asString()).isEqualTo("LOCKED");
			assertThat(finalState.get("members").size()).isEqualTo(joined + 1);
			assertThat(json(lock).get("members").size()).isEqualTo(finalState.get("members").size());
		}
	}

	/** Item adds racing the host's cancel: the cancel response's total must be the final total. */
	@Test
	void itemAddsRacingCancelNeverLandAfterTheCancel() throws Exception {
		setUpCommunity();
		List<String> users = communityMembers(8);
		for (int round = 0; round < ROUNDS; round++) {
			long poolId = createPool(host, communityId, "Round " + round).get("id").asLong();
			for (String u : users) {
				joinPool(u, poolId);
			}

			List<Callable<ResultActions>> calls = new ArrayList<>();
			calls.add(() -> postAuth("/api/pools/" + poolId + "/cancel", host));
			for (String u : users) {
				calls.add(() -> postJson("/api/pools/" + poolId + "/items", u, itemBody("Chips", 1, 1000)));
			}
			List<ResultActions> results = concurrently(calls);
			ResultActions cancel = results.get(0);
			List<ResultActions> adds = results.subList(1, results.size());

			assertThat(statusOf(cancel)).isEqualTo(200);
			long added = count(adds, 201);
			for (ResultActions a : adds) {
				if (statusOf(a) != 201) {
					assertThat(statusOf(a)).isEqualTo(409);
					assertThat(errorOf(a)).isEqualTo("POOL_NOT_OPEN");
				}
			}
			JsonNode finalState = poolDetail(host, poolId);
			assertThat(finalState.get("status").asString()).isEqualTo("CANCELLED");
			assertThat(finalState.get("currentAmountPaise").asLong()).isEqualTo(added * 1000);
			assertThat(json(cancel).get("currentAmountPaise").asLong()).isEqualTo(added * 1000);
		}
	}

	@Test
	void concurrentLocksHaveExactlyOneWinner() throws Exception {
		setUpCommunity();
		for (int round = 0; round < ROUNDS; round++) {
			long poolId = createPool(host, communityId, "Round " + round).get("id").asLong();
			List<Callable<ResultActions>> calls = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				calls.add(() -> postAuth("/api/pools/" + poolId + "/lock", host));
			}
			List<ResultActions> results = concurrently(calls);

			assertThat(count(results, 200)).isEqualTo(1);
			for (ResultActions r : results) {
				if (statusOf(r) != 200) {
					assertThat(statusOf(r)).isEqualTo(409);
					assertThat(errorOf(r)).isEqualTo("INVALID_TRANSITION");
				}
			}
			assertThat(poolDetail(host, poolId).get("status").asString()).isEqualTo("LOCKED");
		}
	}

	/** Lock and cancel at once: either lock-then-cancel (both succeed) or cancel-then-failed-lock. */
	@Test
	void lockRacingCancelEndsCancelled() throws Exception {
		setUpCommunity();
		for (int round = 0; round < ROUNDS; round++) {
			long poolId = createPool(host, communityId, "Round " + round).get("id").asLong();
			List<ResultActions> results = concurrently(List.<Callable<ResultActions>>of(
					() -> postAuth("/api/pools/" + poolId + "/lock", host),
					() -> postAuth("/api/pools/" + poolId + "/cancel", host)));
			ResultActions lock = results.get(0);
			ResultActions cancel = results.get(1);

			assertThat(statusOf(cancel)).isEqualTo(200);
			if (statusOf(lock) != 200) {
				assertThat(statusOf(lock)).isEqualTo(409);
				assertThat(errorOf(lock)).isEqualTo("INVALID_TRANSITION");
			}
			assertThat(poolDetail(host, poolId).get("status").asString()).isEqualTo("CANCELLED");
		}
	}

}
