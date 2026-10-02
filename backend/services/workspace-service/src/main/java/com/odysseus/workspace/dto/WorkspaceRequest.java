package com.odysseus.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Создание и переименование workspace. */
public record WorkspaceRequest(@NotBlank @Size(max = 200) String name) {
}
