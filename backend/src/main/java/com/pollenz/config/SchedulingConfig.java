package com.pollenz.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs (the pool expiry job). Set {@code pollenz.scheduling.enabled=false}
 * to switch them off; the integration tests do, and call the job methods directly instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "pollenz.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
