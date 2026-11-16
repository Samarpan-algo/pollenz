package com.pollenz.pool;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * An unknown {@code platform} fails JSON binding and is answered 400 VALIDATION_FAILED.
 * Amounts are capped at {@value ItemRequest#MAX_PAISE} paise so SQL sums can never overflow a long.
 */
public record CreatePoolRequest(
		@NotNull Platform platform,
		@NotBlank @Size(max = 200) String title,
		@NotNull @Positive @Max(ItemRequest.MAX_PAISE) Long targetAmountPaise,
		@NotNull @Future Instant closesAt,
		@NotBlank @Size(max = 200) String pickupSpot) {
}
