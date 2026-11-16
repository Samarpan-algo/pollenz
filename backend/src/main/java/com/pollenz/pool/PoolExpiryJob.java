package com.pollenz.pool;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes OPEN pools whose {@code closes_at} has passed: LOCKED with at least two members, else
 * EXPIRED. Runs every {@code pollenz.pools.expiry-interval-ms} (default 60 s) when scheduling is
 * enabled (see {@code config.SchedulingConfig}). Tests call {@link #expireDue(Instant)} directly.
 */
@Component
public class PoolExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(PoolExpiryJob.class);

	private final PoolRepository pools;
	private final PoolService poolService;
	private final Clock clock;

	public PoolExpiryJob(PoolRepository pools, PoolService poolService, Clock clock) {
		this.pools = pools;
		this.poolService = poolService;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${pollenz.pools.expiry-interval-ms:60000}",
			initialDelayString = "${pollenz.pools.expiry-interval-ms:60000}")
	public void run() {
		expireDue(clock.instant());
	}

	/**
	 * Processes every pool due at {@code now}, each in its own transaction, so one failing pool does
	 * not stop or roll back the others. Not transactional itself on purpose.
	 */
	public Result expireDue(Instant now) {
		List<Long> due = pools.findIdsDueForExpiry(now);
		int locked = 0;
		int expired = 0;
		int skipped = 0;
		int failed = 0;
		for (Long poolId : due) {
			try {
				Optional<PoolStatus> outcome = poolService.expireIfDue(poolId, now);
				if (outcome.isEmpty()) {
					skipped++;
				}
				else if (outcome.get() == PoolStatus.LOCKED) {
					locked++;
				}
				else {
					expired++;
				}
			}
			catch (RuntimeException ex) {
				failed++;
				log.warn("Expiry failed for pool {}; it will be retried on the next run", poolId, ex);
			}
		}
		if (!due.isEmpty()) {
			log.info("Pool expiry at {}: {} locked, {} expired, {} skipped, {} failed",
					now, locked, expired, skipped, failed);
		}
		return new Result(locked, expired, skipped, failed);
	}

	/** Counts for one run. {@code skipped}: no longer OPEN or due when its row lock was taken. */
	public record Result(int locked, int expired, int skipped, int failed) {
	}

}
