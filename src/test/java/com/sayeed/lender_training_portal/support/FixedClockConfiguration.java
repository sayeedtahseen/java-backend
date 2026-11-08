package com.sayeed.lender_training_portal.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the production {@code Clock.systemUTC()} with a clock that never moves, so tests can assert
 * exact timestamps ({@code enrolledAt}, {@code completedAt}).
 *
 * <p>Why "now" and not a constant like 2025-01-01? {@code TokenService} stamps {@code iat}/{@code exp} with
 * this clock, but Spring Security's {@code JwtTimestampValidator} checks {@code exp} against the real
 * system clock. A clock frozen in the past would make every freshly issued token look expired.
 * Freezing at context start keeps tokens valid for the 1h TTL, which is far longer than the test run.
 *
 * <p>Truncated to seconds so values survive the JSON and timestamptz round trips unchanged.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    @Bean
    @Primary
    Clock fixedTestClock() {
        return Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
    }
}
