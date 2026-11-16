package com.pollenz;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The application clock in tests: system UTC until a test freezes it. {@link IntegrationTestBase}
 * resets it after every test, so a frozen time never leaks into the next one.
 */
public class MutableClock extends Clock {

	private volatile Instant frozen;

	/** From now on, {@link #instant()} returns exactly {@code instant}. */
	public void freezeAt(Instant instant) {
		this.frozen = instant;
	}

	/** Back to the system clock. */
	public void reset() {
		this.frozen = null;
	}

	@Override
	public Instant instant() {
		Instant f = frozen;
		return f != null ? f : Instant.now();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("The test clock is always UTC");
	}

}
