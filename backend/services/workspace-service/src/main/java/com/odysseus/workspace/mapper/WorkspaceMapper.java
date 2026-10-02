package com.odysseus.workspace.mapper;

import com.odysseus.workspace.dto.WorkspaceResponse;
import com.odysseus.workspace.entity.Workspace;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    WorkspaceResponse toResponse(Workspace workspace);
}
