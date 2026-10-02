package com.odysseus.workspace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Строка outbox. Без {@code @TenantId}: читается планировщиком в системном контексте, workspace хранится как данные. */
@Entity
@Table(name = "outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Outbox {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Outbox other && id != null && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
