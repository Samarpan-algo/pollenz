package com.pollenz.community;

import java.time.Instant;

public record MemberResponse(Long userId, String name, Role role, Instant joinedAt) {
}
