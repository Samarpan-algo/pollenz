package com.pollenz.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application clock, system UTC. Code that needs "now" for a rule (pool deadlines, payment
 * times, the expiry job) takes this bean instead of calling {@code Instant.now()}, so tests can
 * control time.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
