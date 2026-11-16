package com.pollenz.pool;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of add item (POST) and edit item (PUT, full replace). {@code estPricePaise} is the line
 * total for the whole quantity, not a unit price.
 */
public record ItemRequest(
		@NotBlank @Size(max = 200) String name,
		@NotNull @Min(1) Integer quantity,
		@NotNull @Positive @Max(MAX_PAISE) Long estPricePaise) {

	/** Rs 1 crore. A sanity cap that keeps per-pool sums far from long overflow. */
	public static final long MAX_PAISE = 1_000_000_000L;

}
