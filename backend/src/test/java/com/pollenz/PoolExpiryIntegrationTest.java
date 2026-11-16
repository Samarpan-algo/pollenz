package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.pollenz.pool.PoolExpiryJob;

import tools.jackson.databind.JsonNode;

/**
 * The expiry job, called directly with the current time. Each test moves only its own pools'
 * {@code closes_at} into the past; other tests' pools close a day from now, so they are not touched.
 */
class PoolExpiryIntegrationTest extends IntegrationTestBase {

	@Autowired
	private PoolExpiryJob expiryJob;

	@Autowired
	private JdbcTemplate jdbc;

	private String host;
	private String ravi;
	private long communityId;

	private void setUpCommunity() throws Exception {
		host = registerToken("Asha");
		ravi = registerToken("Ravi");
		JsonNode community = createCommunity(host, "Hostel");
		joinCommunity(ravi, community);
		communityId = community.get("id").asLong();
	}

	/**
	 * Moves the pool's deadline to {@code ago} before now (the API only accepts future deadlines). A
	 * negative {@code ago} puts it that far ahead.
	 */
	private void makeDue(long poolId, Duration ago) {
		jdbc.update("update pools set closes_at = ? where id = ?", Timestamp.from(Instant.now().minus(ago)), poolId);
	}

	private String poolStatus(long poolId) throws Exception {
		return poolDetail(host, poolId).get("status").asString();
	}

	@Test
	void duePoolWithTwoMembersIsLocked() throws Exception {
		setUpCommunity();
		long poolId = createPool(host, communityId, "P").get("id").asLong();
		joinPool(ravi, poolId);
		makeDue(poolId, Duration.ofMinutes(1));

		PoolExpiryJob.Result result = expiryJob.expireDue(Instant.now());

		assertThat(result.locked()).isGreaterThanOrEqualTo(1);
		assertThat(poolStatus(poolId)).isEqualTo("LOCKED");
		// A locked pool can still be cancelled by the host.
		postAuth("/api/pools/" + poolId + "/cancel", host).andExpect(status().isOk());
	}

	@Test
	void duePoolWithOnlyTheHostExpires() throws Exception {
		setUpCommunity();
		long poolId = createPool(host, communityId, "P").get("id").asLong();
		makeDue(poolId, Duration.ofMinutes(1));

		PoolExpiryJob.Result result = expiryJob.expireDue(Instant.now());

		assertThat(result.expired()).isGreaterThanOrEqualTo(1);
		assertThat(poolStatus(poolId)).isEqualTo("EXPIRED");
		// EXPIRED is final.
		for (String action : new String[] { "lock", "cancel" }) {
			postAuth("/api/pools/" + poolId + "/" + action, host)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
		}
		postAuth("/api/pools/" + poolId + "/join", ravi)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("POOL_NOT_OPEN"));
		getAuth("/api/communities/" + communityId + "/pools?status=EXPIRED", host)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(poolId));
	}

	@Test
	void memberWhoLeftDoesNotCount() throws Exception {
		setUpCommunity();
		long poolId = createPool(host, communityId, "P").get("id").asLong();
		joinPool(ravi, poolId);
		deleteAuth("/api/pools/" + poolId + "/leave", ravi).andExpect(status().isNoContent());
		makeDue(poolId, Duration.ofMinutes(1));

		expiryJob.expireDue(Instant.now());

		assertThat(poolStatus(poolId)).isEqualTo("EXPIRED");
	}

	@Test
	void poolsNotDueOrNotOpenAreLeftAlone() throws Exception {
		setUpCommunity();
		// Closes in 10 minutes; other tests' pools close in a day, so the run at +20 min below skips them.
		long future = json(postJson("/api/communities/" + communityId + "/pools", host,
				poolBody("BLINKIT", "Future", 19900, Instant.now().plus(Duration.ofMinutes(10)), "Gate 2"))
				.andExpect(status().isCreated())).get("id").asLong();
		joinPool(ravi, future);
		long cancelled = createPool(host, communityId, "Cancelled").get("id").asLong();
		postAuth("/api/pools/" + cancelled + "/cancel", host).andExpect(status().isOk());
		makeDue(cancelled, Duration.ofMinutes(1));

		expiryJob.expireDue(Instant.now());

		assertThat(poolStatus(future)).isEqualTo("OPEN");
		assertThat(poolStatus(cancelled)).isEqualTo("CANCELLED");
		// The same pool is due once the clock passes its deadline.
		expiryJob.expireDue(Instant.now().plus(Duration.ofMinutes(20)));
		assertThat(poolStatus(future)).isEqualTo("LOCKED");
	}

	@Test
	void oneFailingPoolDoesNotStopTheOthers() throws Exception {
		setUpCommunity();
		long first = createPool(host, communityId, "First").get("id").asLong();
		long failing = createPool(host, communityId, "Failing").get("id").asLong();
		long last = createPool(host, communityId, "Last").get("id").asLong();
		joinPool(ravi, last);
		// The job handles due pools oldest deadline first: the failing one sits in the middle.
		makeDue(first, Duration.ofMinutes(3));
		makeDue(failing, Duration.ofMinutes(2));
		makeDue(last, Duration.ofMinutes(1));

		// A trigger makes any UPDATE of that one pool row fail inside the database.
		jdbc.execute("""
				create or replace function pollenz_test_reject_update() returns trigger as $$
				begin
					raise exception 'test: update of pool % rejected', old.id;
				end $$ language plpgsql""");
		String trigger = "pollenz_test_reject_" + failing;
		jdbc.execute("create trigger " + trigger + " before update on pools for each row when (old.id = "
				+ failing + ") execute function pollenz_test_reject_update()");
		PoolExpiryJob.Result result;
		try {
			result = expiryJob.expireDue(Instant.now());
		}
		finally {
			jdbc.execute("drop trigger if exists " + trigger + " on pools");
		}

		assertThat(result.failed()).isGreaterThanOrEqualTo(1);
		assertThat(poolStatus(first)).isEqualTo("EXPIRED");
		assertThat(poolStatus(failing)).isEqualTo("OPEN");
		assertThat(poolStatus(last)).isEqualTo("LOCKED");

		// With the fault gone, the next run picks the pool up.
		expiryJob.expireDue(Instant.now());
		assertThat(poolStatus(failing)).isEqualTo("EXPIRED");
	}

	@Test
	void expiryRacingHostLockHasExactlyOneWinner() throws Exception {
		setUpCommunity();
		for (int round = 0; round < 5; round++) {
			// Only the host is in the pool: the job would EXPIRE it, the host would LOCK it.
			long poolId = createPool(host, communityId, "Race " + round).get("id").asLong();
			makeDue(poolId, Duration.ofSeconds(1));

			List<Object> results = concurrently(List.<Callable<Object>>of(
					() -> postAuth("/api/pools/" + poolId + "/lock", host),
					() -> expiryJob.expireDue(Instant.now())));
			ResultActions lock = (ResultActions) results.get(0);
			PoolExpiryJob.Result job = (PoolExpiryJob.Result) results.get(1);

			String status = poolStatus(poolId);
			if (statusOf(lock) == 200) {
				assertThat(status).isEqualTo("LOCKED");
			}
			else {
				assertThat(statusOf(lock)).isEqualTo(409);
				assertThat(errorOf(lock)).isEqualTo("INVALID_TRANSITION");
				assertThat(status).isEqualTo("EXPIRED");
			}
			assertThat(job.failed()).isZero();
		}
	}

	/**
	 * A join racing the job. Joins are refused once {@code closes_at} has passed, so the deadline stays
	 * 30 s ahead for the join while the job runs as of a minute from now: either may take the row lock
	 * first.
	 */
	@Test
	void expiryRacingJoinCountsTheMemberConsistently() throws Exception {
		setUpCommunity();
		for (int round = 0; round < 5; round++) {
			long poolId = createPool(host, communityId, "Race " + round).get("id").asLong();
			makeDue(poolId, Duration.ofSeconds(-30));

			List<Object> results = concurrently(List.<Callable<Object>>of(
					() -> postAuth("/api/pools/" + poolId + "/join", ravi),
					() -> expiryJob.expireDue(Instant.now().plus(Duration.ofMinutes(1)))));
			ResultActions join = (ResultActions) results.get(0);

			JsonNode detail = poolDetail(host, poolId);
			if (statusOf(join) == 200) {
				// The join committed first, so the job saw two members.
				assertThat(detail.get("status").asString()).isEqualTo("LOCKED");
				assertThat(detail.get("members").size()).isEqualTo(2);
			}
			else {
				assertThat(errorOf(join)).isEqualTo("POOL_NOT_OPEN");
				assertThat(detail.get("status").asString()).isEqualTo("EXPIRED");
				assertThat(detail.get("members").size()).isEqualTo(1);
			}
		}
	}

}
