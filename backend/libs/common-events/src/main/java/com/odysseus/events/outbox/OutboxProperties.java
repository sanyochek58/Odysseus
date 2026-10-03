package com.odysseus.events.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Параметры {@code odysseus.outbox.*}. Интервал опроса читается планировщиком напрямую из
 * {@code poll-interval-ms} (по умолчанию 1000).
 */
@ConfigurationProperties("odysseus.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") int batchSize,
        @DefaultValue("1000") long pollIntervalMs,
        @DefaultValue Retry retry) {

    /** Retry консьюмера перед отправкой в {@code <topic>.dlt}. */
    public record Retry(
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("1000") long initialIntervalMs,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("30000") long maxIntervalMs) {
    }
}
