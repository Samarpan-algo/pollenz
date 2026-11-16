package com.pollenz.pool;

/**
 * Domain event published by {@link PoolService} inside its transaction. Listeners that must only see
 * committed data use {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 *
 * @param actorId the user who caused the change, or null for the expiry job
 * @param status  the pool status after the change; set for POOL_UPDATED, null otherwise
 */
public record PoolEvent(long poolId, PoolEventType type, Long actorId, PoolStatus status) {
}
