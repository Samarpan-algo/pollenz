package com.pollenz.pool;

import java.time.Instant;
import java.util.List;

/**
 * Pool detail. {@code hostUpiId} is set only when the caller is a member of this pool; {@code feesPaise}
 * and the members' {@code sharePaise} stay null until the {@code order} action.
 */
public record PoolDetailResponse(Long id, Long communityId, Platform platform, String title, long targetAmountPaise,
		long currentAmountPaise, Instant closesAt, String pickupSpot, PoolStatus status, Long feesPaise,
		Long hostId, String hostName, String hostUpiId, Instant createdAt, List<PoolMemberResponse> members) {
}
