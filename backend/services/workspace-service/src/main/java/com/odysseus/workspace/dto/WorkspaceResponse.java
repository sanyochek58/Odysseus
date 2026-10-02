package com.odysseus.workspace.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {
}
