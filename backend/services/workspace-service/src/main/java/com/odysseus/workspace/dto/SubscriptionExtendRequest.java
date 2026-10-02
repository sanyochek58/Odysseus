package com.odysseus.workspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Продление на число дней. Платежей нет, продление подтверждает владелец. */
public record SubscriptionExtendRequest(@Min(1) @Max(366) int days) {
}
