package com.pollenz.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pollenz.pool.PoolStatus;

/** Small payload of every non-chat event. Clients refetch the pool detail over REST. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PoolEventPayload(long poolId, Long actorId, PoolStatus status) {
}
