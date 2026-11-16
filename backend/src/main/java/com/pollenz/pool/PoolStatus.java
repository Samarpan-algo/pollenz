package com.pollenz.pool;

/**
 * Every status from the SPEC lifecycle. Which status can follow which is defined only in
 * {@link PoolTransition}.
 */
public enum PoolStatus {
	OPEN, LOCKED, EXPIRED, CANCELLED, ORDERED, DELIVERED, SETTLED
}
