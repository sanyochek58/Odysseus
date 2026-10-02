package com.odysseus.workspace.mapper;

import com.odysseus.workspace.dto.SubscriptionResponse;
import com.odysseus.workspace.entity.Subscription;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    SubscriptionResponse toResponse(Subscription subscription);
}
