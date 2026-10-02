package com.odysseus.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.odysseus.workspace.entity.Outbox;
import com.odysseus.workspace.event.SubscriptionChangedEvent;
import com.odysseus.workspace.repository.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class OutboxServiceTest {

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final OutboxService service = new OutboxService(repository, JsonMapper.builder().build());

    @Test
    @DisplayName("save: сериализует событие в JSON и пишет строку outbox")
    void save_event_storesJsonPayload() {
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        var event = new SubscriptionChangedEvent(eventId, Instant.parse("2026-10-03T12:00:00Z"), workspaceId, 1,
                Instant.parse("2026-11-03T12:00:00Z"));

        service.save(SubscriptionChangedEvent.TOPIC, workspaceId, workspaceId, eventId, event);

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(repository).save(captor.capture());
        Outbox row = captor.getValue();
        assertThat(row.getId()).isEqualTo(eventId);
        assertThat(row.getTopic()).isEqualTo("workspace.subscription.changed");
        assertThat(row.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(row.getPayload()).contains(eventId.toString()).contains("expiresAt");
    }
}
