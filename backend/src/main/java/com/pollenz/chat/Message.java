package com.pollenz.chat;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pool_id", nullable = false)
	private Long poolId;

	@Column(name = "sender_id", nullable = false)
	private Long senderId;

	@Column(nullable = false)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Message() {
	}

	public Message(Long poolId, Long senderId, String body, Instant createdAt) {
		this.poolId = poolId;
		this.senderId = senderId;
		this.body = body;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getPoolId() {
		return poolId;
	}

	public Long getSenderId() {
		return senderId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
