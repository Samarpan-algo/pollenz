package com.pollenz.pool;

import java.time.Instant;
import java.util.List;

public record PoolDetailResponse(Long id, Long communityId, Platform platform, String title, long targetAmountPaise,
		long currentAmountPaise, Instant closesAt, String pickupSpot, PoolStatus status, Long feesPaise,
		Long hostId, String hostName, String hostUpiId, Instant createdAt, List<PoolMemberResponse> members) {
}
