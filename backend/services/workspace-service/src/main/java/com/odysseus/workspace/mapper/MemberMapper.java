package com.odysseus.workspace.mapper;

import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.entity.Member;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    MemberResponse toResponse(Member member);
}
