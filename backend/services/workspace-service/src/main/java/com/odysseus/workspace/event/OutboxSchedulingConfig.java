package com.odysseus.workspace.event;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включает планировщик outbox. Бин Clock в {@code ClockConfig}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class OutboxSchedulingConfig {
}
