package com.pollenz.community;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "communities")
public class Community {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "invite_code", nullable = false)
	private String inviteCode;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Community() {
	}

	public Community(String name, String inviteCode, Long createdBy, Instant createdAt) {
		this.name = name;
		this.inviteCode = inviteCode;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public String getInviteCode() { return inviteCode; }
	public Long getCreatedBy() { return createdBy; }
	public Instant getCreatedAt() { return createdAt; }

}
