package com.pollenz.pool;

import java.util.EnumSet;
import java.util.Set;

/**
 * The pool state machine: every allowed transition, with the statuses it may start from and the
 * status it leads to. {@link Pool#apply(PoolTransition)} is the only code that changes a pool's
 * status, so a new transition (phase 4: ORDER, DELIVER, SETTLE) is added here and nowhere else.
 */
public enum PoolTransition {

	/** Host locks, or the expiry job locks a due pool with at least two members. */
	LOCK(PoolStatus.LOCKED, PoolStatus.OPEN),

	/** Expiry job: a due pool with fewer than two members. */
	EXPIRE(PoolStatus.EXPIRED, PoolStatus.OPEN),

	/** Host cancels. */
	CANCEL(PoolStatus.CANCELLED, PoolStatus.OPEN, PoolStatus.LOCKED);

	private final PoolStatus target;
	private final Set<PoolStatus> from;

	PoolTransition(PoolStatus target, PoolStatus first, PoolStatus... rest) {
		this.target = target;
		this.from = EnumSet.of(first, rest);
	}

	public PoolStatus target() {
		return target;
	}

	public boolean allowedFrom(PoolStatus status) {
		return from.contains(status);
	}

}
