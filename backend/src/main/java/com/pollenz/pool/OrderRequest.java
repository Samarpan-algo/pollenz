package com.pollenz.pool;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of the {@code order} action. {@code items} must list every item of the pool exactly once;
 * that is checked by the service against the pool, after the pool's status (400 VALIDATION_FAILED).
 * An empty list is valid only for a pool with no items.
 */
public record OrderRequest(
		@NotNull @PositiveOrZero @Max(ItemRequest.MAX_PAISE) Long feesPaise,
		@NotNull List<@NotNull @Valid OrderItemRequest> items) {
}
