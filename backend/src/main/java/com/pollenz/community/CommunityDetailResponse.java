package com.pollenz.community;

import java.time.Instant;
import java.util.List;

public record CommunityDetailResponse(Long id, String name, String inviteCode, Instant createdAt,
		List<MemberResponse> members) {
}
