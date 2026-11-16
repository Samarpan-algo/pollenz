package com.pollenz.pool;

import java.time.Instant;

/**
 * A pool card in a community's list. {@code currentAmountPaise} is the sum of the items'
 * {@code estPricePaise}; both aggregates come from the list query itself.
 */
public record PoolSummary(Long id, Long communityId, Platform platform, String title, long targetAmountPaise,
		long currentAmountPaise, long memberCount, Instant closesAt, String pickupSpot, PoolStatus status,
		Long hostId, String hostName, Instant createdAt) {
}
