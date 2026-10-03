package com.odysseus.workspace.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.odysseus.events.SubscriptionChangedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Outbox на реальных Postgres и Kafka: событие из транзакции доходит до топика с полями контракта. */
class OutboxKafkaIT extends IntegrationTestBase {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private List<ConsumerRecord<String, String>> awaitRecords(String key, int expected) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<ConsumerRecord<String, String>> found = new java.util.ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(SubscriptionChangedEvent.TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (found.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        found.add(r);
                    }
                });
            }
        }
        return found;
    }

    @Test
    @DisplayName("POST /workspaces: событие workspace.subscription.changed в топике с корректными полями контракта")
    void createWorkspace_publishesSubscriptionChanged() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        Instant before = Instant.now();

        mvc.perform(post("/api/v1/workspaces").with(token(workspaceId, "owner-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"))
                .andExpect(status().isCreated());

        List<ConsumerRecord<String, String>> records = awaitRecords(workspaceId.toString(), 1);
        assertThat(records).hasSize(1);
        JsonNode event = jsonMapper.readTree(records.get(0).value());
        assertThat(UUID.fromString(event.get("eventId").asString())).isNotNull();
        assertThat(UUID.fromString(event.get("workspaceId").asString())).isEqualTo(workspaceId);
        assertThat(event.get("version").asInt()).isEqualTo(1);
        assertThat(Instant.parse(event.get("occurredAt").asString())).isAfterOrEqualTo(before.minusSeconds(1));
        Instant expiresAt = Instant.parse(event.get("expiresAt").asString());
        assertThat(expiresAt).isAfter(Instant.now().plus(java.time.Duration.ofDays(29)));

        // строка outbox помечена отправленной, id строки равен eventId
        Integer sent = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE workspace_id = ? AND sent_at IS NOT NULL AND id = ?",
                Integer.class, workspaceId, UUID.fromString(event.get("eventId").asString()));
        assertThat(sent).isEqualTo(1);
    }

    @Test
    @DisplayName("продление подписки: второе событие с новым expiresAt, ключ партиции workspaceId")
    void extendSubscription_publishesSecondEventWithNewExpiry() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        mvc.perform(post("/api/v1/workspaces").with(token(workspaceId, "owner-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/subscriptions/current/extensions").with(token(workspaceId, "owner-1"))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":10}"))
                .andExpect(status().isOk());

        List<ConsumerRecord<String, String>> records = awaitRecords(workspaceId.toString(), 2);
        assertThat(records).hasSize(2);
        Instant first = Instant.parse(jsonMapper.readTree(records.get(0).value()).get("expiresAt").asString());
        Instant second = Instant.parse(jsonMapper.readTree(records.get(1).value()).get("expiresAt").asString());
        assertThat(second).isAfter(first.plus(Duration.ofDays(9)));
    }

    @Test
    @DisplayName("откат бизнес-операции: событие в outbox не попадает")
    void conflictingCreate_doesNotAddSecondOutboxRow() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/workspaces").with(token(workspaceId, "owner-1"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"));
        }
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE workspace_id = ?", Integer.class, workspaceId);
        assertThat(rows).isEqualTo(1);
    }
}
