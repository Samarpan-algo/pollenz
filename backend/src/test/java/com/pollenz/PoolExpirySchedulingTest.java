package com.pollenz;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The one test with scheduling switched on, at a short interval, to prove the {@code @Scheduled}
 * wiring and the interval property. Its own context (and database) is closed afterwards so the
 * job never runs alongside other tests.
 */
@SpringBootTest(properties = { "pollenz.scheduling.enabled=true", "pollenz.pools.expiry-interval-ms=200" })
@DirtiesContext
class PoolExpirySchedulingTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void scheduledJobExpiresDuePools() throws Exception {
		String host = registerToken("Asha");
		long poolId = createPool(host, createCommunity(host, "Hostel").get("id").asLong(), "P").get("id").asLong();
		jdbc.update("update pools set closes_at = ? where id = ?",
				Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))), poolId);

		Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
		String status = "OPEN";
		while (status.equals("OPEN") && Instant.now().isBefore(deadline)) {
			Thread.sleep(100);
			status = poolDetail(host, poolId).get("status").asString();
		}
		assertThat(status).isEqualTo("EXPIRED");
	}

}
