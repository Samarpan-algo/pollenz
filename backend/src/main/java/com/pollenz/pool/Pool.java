package com.pollenz.pool;

import java.time.Instant;
import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.pollenz.common.ApiException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "pools")
public class Pool {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "community_id", nullable = false)
	private Long communityId;

	@Column(name = "host_id", nullable = false)
	private Long hostId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Platform platform;

	@Column(nullable = false)
	private String title;

	@Column(name = "target_amount_paise", nullable = false)
	private long targetAmountPaise;

	@Column(name = "closes_at", nullable = false)
	private Instant closesAt;

	@Column(name = "pickup_spot", nullable = false)
	private String pickupSpot;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PoolStatus status;

	/** Set by the {@code order} action (phase 4); null before that. */
	@Column(name = "fees_paise")
	private Long feesPaise;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Pool() {
	}

	public Pool(Long communityId, Long hostId, Platform platform, String title, long targetAmountPaise,
			Instant closesAt, String pickupSpot, Instant createdAt) {
		this.communityId = communityId;
		this.hostId = hostId;
		this.platform = platform;
		this.title = title;
		this.targetAmountPaise = targetAmountPaise;
		this.closesAt = closesAt;
		this.pickupSpot = pickupSpot;
		this.status = PoolStatus.OPEN;
		this.createdAt = createdAt;
	}

	/**
	 * The only way to change the status. Throws 409 INVALID_TRANSITION unless {@link PoolTransition}
	 * allows {@code t} from the current status.
	 */
	public void apply(PoolTransition t) {
		if (!t.allowedFrom(status)) {
			throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION",
					"Cannot " + t.name().toLowerCase(Locale.ROOT) + " a pool that is " + status);
		}
		status = t.target();
	}

	/** Join, leave and item changes are allowed only while OPEN: throws 409 POOL_NOT_OPEN otherwise. */
	public void requireOpen() {
		if (status != PoolStatus.OPEN) {
			throw new ApiException(HttpStatus.CONFLICT, "POOL_NOT_OPEN", "The pool is " + status + ", not OPEN");
		}
	}

	public boolean isHost(long userId) {
		return hostId == userId;
	}

	public Long getId() { return id; }
	public Long getCommunityId() { return communityId; }
	public Long getHostId() { return hostId; }
	public Platform getPlatform() { return platform; }
	public String getTitle() { return title; }
	public long getTargetAmountPaise() { return targetAmountPaise; }
	public Instant getClosesAt() { return closesAt; }
	public String getPickupSpot() { return pickupSpot; }
	public PoolStatus getStatus() { return status; }
	public Long getFeesPaise() { return feesPaise; }
	public Long getVersion() { return version; }
	public Instant getCreatedAt() { return createdAt; }
	public void setFeesPaise(Long feesPaise) { this.feesPaise = feesPaise; }

}
