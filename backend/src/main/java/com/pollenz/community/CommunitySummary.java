package com.pollenz.community;

import java.time.Instant;

/** A community as seen by one of its members; {@code role} is that member's role. */
public record CommunitySummary(Long id, String name, String inviteCode, Role role, Instant createdAt) {
}
