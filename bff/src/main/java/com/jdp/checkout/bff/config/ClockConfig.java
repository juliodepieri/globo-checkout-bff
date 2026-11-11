package com.jdp.checkout.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ClockConfig {

    /** Injected everywhere time matters, so tests can fix "now". */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
