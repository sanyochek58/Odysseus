package com.odysseus.workspace.event;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включает планировщик outbox и даёт общий {@link Clock} (подменяется в тестах). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class OutboxSchedulingConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
