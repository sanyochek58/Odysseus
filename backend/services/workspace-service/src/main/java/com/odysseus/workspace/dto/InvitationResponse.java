package com.odysseus.workspace.dto;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.entity.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        String email,
        WorkspaceRole role,
        InvitationStatus status,
        String invitedBy,
        Instant expiresAt,
        Instant createdAt) {
}
