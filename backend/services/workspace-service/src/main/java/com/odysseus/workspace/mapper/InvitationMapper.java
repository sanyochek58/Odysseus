package com.odysseus.workspace.mapper;

import com.odysseus.workspace.dto.InvitationResponse;
import com.odysseus.workspace.entity.Invitation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InvitationMapper {

    InvitationResponse toResponse(Invitation invitation);
}
