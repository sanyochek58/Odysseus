package com.odysseus.workspace.dto;

import com.odysseus.workspace.config.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvitationRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull WorkspaceRole role) {
}
