package com.pollenz.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "upi_id")
	private String upiId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected User() {
	}

	public User(String name, String email, String passwordHash, Instant createdAt) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
	public String getUpiId() { return upiId; }
	public Instant getCreatedAt() { return createdAt; }
	public void setName(String name) { this.name = name; }
	public void setUpiId(String upiId) { this.upiId = upiId; }

}
