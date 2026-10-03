package com.odysseus.events;

import com.odysseus.events.consumer.IdempotentEventProcessor;
import com.odysseus.events.consumer.ProcessedEventStore;
import com.odysseus.events.outbox.OutboxProperties;
import com.odysseus.events.outbox.OutboxPublisher;
import com.odysseus.events.outbox.OutboxRelay;
import com.odysseus.events.outbox.OutboxService;
import com.odysseus.events.outbox.store.Outbox;
import com.odysseus.events.outbox.store.OutboxRepository;
import java.time.Clock;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * Автоконфигурация outbox и идемпотентного консьюмера. Параметры {@code odysseus.outbox.*}.
 * Регистрирует пакет сущности {@link Outbox} для сканирования JPA вместе с пакетами приложения.
 * Отключается {@code odysseus.outbox.enabled=false}.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration"})
@ConditionalOnProperty(prefix = "odysseus.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigurationPackage(basePackageClasses = Outbox.class)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock outboxClock() {
        return Clock.systemUTC();
    }

    /** Без бина сервиса тенант не привязывается. Сервис с тенантностью обязан объявить свой {@link TenantScope}. */
    @Bean
    @ConditionalOnMissingBean
    TenantScope outboxTenantScope() {
        return TenantScope.NONE;
    }

    @Bean
    OutboxService outboxService(OutboxRepository repository, JsonMapper jsonMapper) {
        return new OutboxService(repository, jsonMapper);
    }

    @Bean
    OutboxRelay outboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate, Clock clock) {
        return new OutboxRelay(repository, kafkaTemplate, clock);
    }

    @Bean
    OutboxPublisher outboxPublisher(OutboxRelay relay, TenantScope tenantScope, OutboxProperties properties) {
        return new OutboxPublisher(relay, tenantScope, properties);
    }

    @Bean
    ProcessedEventStore processedEventStore(JdbcClient jdbcClient) {
        return new ProcessedEventStore(jdbcClient);
    }

    @Bean
    IdempotentEventProcessor idempotentEventProcessor(ProcessedEventStore store,
            PlatformTransactionManager transactionManager, TenantScope tenantScope) {
        return new IdempotentEventProcessor(store, new TransactionTemplate(transactionManager), tenantScope);
    }

    /** Retry с экспоненциальным бэкоффом, затем запись в {@code <topic>.dlt}. */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler outboxConsumerErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", -1));
        return new DefaultErrorHandler(recoverer, backOff(properties.retry()));
    }

    static ExponentialBackOff backOff(OutboxProperties.Retry retry) {
        var backOff = new ExponentialBackOff(retry.initialIntervalMs(), retry.multiplier());
        backOff.setMaxInterval(retry.maxIntervalMs());
        backOff.setMaxAttempts(retry.maxAttempts());
        return backOff;
    }
}
