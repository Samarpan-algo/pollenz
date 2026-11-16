package com.pollenz.pool;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** One line of the {@code order} body: the item's final line total; 0 means it was unavailable. */
public record OrderItemRequest(
		@NotNull Long itemId,
		@NotNull @PositiveOrZero @Max(ItemRequest.MAX_PAISE) Long finalPricePaise) {
}
