package com.pollenz.pool;

/** What changed in a pool. The realtime layer forwards these to clients under the same names. */
public enum PoolEventType {
	POOL_UPDATED, MEMBER_JOINED, MEMBER_LEFT, ITEMS_CHANGED, PAYMENT_UPDATED
}
