package com.odysseus.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odysseus.events.outbox.store.Outbox;
import com.odysseus.events.outbox.store.OutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final OutboxRepository repository = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(repository, kafka, Clock.fixed(NOW, ZoneOffset.UTC));

    private Outbox row(UUID aggregateId) {
        return Outbox.builder().id(UUID.randomUUID()).topic("a.b.c").aggregateId(aggregateId)
                .workspaceId(UUID.randomUUID()).payload("{}").build();
    }

    @Test
    @DisplayName("publishBatch: шлёт с ключом aggregateId и ставит sentAt")
    void publishBatch_rows_sendsAndMarksSent() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        Outbox row = row(aggregateId);
        when(repository.lockUnsent(10)).thenReturn(List.of(row));
        when(kafka.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        int sent = relay.publishBatch(10);

        assertThat(sent).isEqualTo(1);
        assertThat(row.getSentAt()).isEqualTo(NOW);
        verify(kafka).send(eq("a.b.c"), eq(aggregateId.toString()), eq("{}"));
    }

    @Test
    @DisplayName("publishBatch: пустая пачка ничего не шлёт")
    void publishBatch_empty_sendsNothing() throws Exception {
        when(repository.lockUnsent(10)).thenReturn(List.of());

        assertThat(relay.publishBatch(10)).isZero();
        verify(kafka, never()).send(any(String.class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("publishBatch: ошибка отправки не ставит sentAt и пробрасывается")
    void publishBatch_sendFails_rowStaysUnsent() {
        Outbox row = row(UUID.randomUUID());
        when(repository.lockUnsent(10)).thenReturn(List.of(row));
        when(kafka.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(new IllegalStateException("down")));

        assertThatThrownBy(() -> relay.publishBatch(10)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(row.getSentAt()).isNull();
    }
}
