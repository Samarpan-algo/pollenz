package com.pollenz.pool;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pool_members")
public class PoolMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pool_id", nullable = false)
	private Long poolId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** Set by the {@code order} action (phase 4); null before that. */
	@Column(name = "share_paise")
	private Long sharePaise;

	@Column(nullable = false)
	private boolean paid;

	@Column(name = "paid_at")
	private Instant paidAt;

	protected PoolMember() {
	}

	public PoolMember(Long poolId, Long userId) {
		this.poolId = poolId;
		this.userId = userId;
	}

	public Long getId() { return id; }
	public Long getPoolId() { return poolId; }
	public Long getUserId() { return userId; }
	public Long getSharePaise() { return sharePaise; }
	public boolean isPaid() { return paid; }
	public Instant getPaidAt() { return paidAt; }
	public void setSharePaise(Long sharePaise) { this.sharePaise = sharePaise; }
	public void setPaid(boolean paid) { this.paid = paid; }
	public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

}
