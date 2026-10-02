package com.odysseus.workspace.dto;

import com.odysseus.workspace.config.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(UUID id, String userId, String email, WorkspaceRole role, Instant createdAt) {
}
