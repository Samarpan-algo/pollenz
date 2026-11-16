package com.pollenz;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the application {@code Clock} with a {@link MutableClock} that tests can freeze. */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

}
