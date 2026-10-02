package com.odysseus.workspace.dto;

import com.odysseus.workspace.config.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record MemberRoleRequest(@NotNull WorkspaceRole role) {
}
