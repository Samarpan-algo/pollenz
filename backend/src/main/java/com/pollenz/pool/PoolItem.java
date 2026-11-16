package com.pollenz.pool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One line of a member's order. Prices are line totals (quantity already included), in paise. */
@Entity
@Table(name = "pool_items")
public class PoolItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pool_member_id", nullable = false)
	private Long poolMemberId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "est_price_paise", nullable = false)
	private long estPricePaise;

	/** Set by the {@code order} action (phase 4); null before that. */
	@Column(name = "final_price_paise")
	private Long finalPricePaise;

	protected PoolItem() {
	}

	public PoolItem(Long poolMemberId, String name, int quantity, long estPricePaise) {
		this.poolMemberId = poolMemberId;
		this.name = name;
		this.quantity = quantity;
		this.estPricePaise = estPricePaise;
	}

	public void update(String name, int quantity, long estPricePaise) {
		this.name = name;
		this.quantity = quantity;
		this.estPricePaise = estPricePaise;
	}

	public Long getId() { return id; }
	public Long getPoolMemberId() { return poolMemberId; }
	public String getName() { return name; }
	public int getQuantity() { return quantity; }
	public long getEstPricePaise() { return estPricePaise; }
	public Long getFinalPricePaise() { return finalPricePaise; }
	public void setFinalPricePaise(Long finalPricePaise) { this.finalPricePaise = finalPricePaise; }

}
