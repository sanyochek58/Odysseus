package com.odysseus.workspace.dto;

import java.time.Instant;

public record SubscriptionResponse(Instant expiresAt, Instant createdAt, Instant updatedAt) {
}
