package com.pollenz.community;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "memberships")
public class Membership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "community_id", nullable = false)
	private Long communityId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	protected Membership() {
	}

	public Membership(Long userId, Long communityId, Role role, Instant joinedAt) {
		this.userId = userId;
		this.communityId = communityId;
		this.role = role;
		this.joinedAt = joinedAt;
	}

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public Long getCommunityId() { return communityId; }
	public Role getRole() { return role; }
	public Instant getJoinedAt() { return joinedAt; }

}
