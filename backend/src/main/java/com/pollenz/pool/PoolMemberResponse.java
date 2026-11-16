package com.pollenz.pool;

import java.time.Instant;
import java.util.List;

/**
 * A pool member. {@code memberId} is the pool_members id (the {@code {memberId}} of the paid route);
 * {@code sharePaise} stays null until the {@code order} action.
 */
public record PoolMemberResponse(Long memberId, Long userId, String name, Long sharePaise, boolean paid,
		Instant paidAt, List<PoolItemResponse> items) {
}
