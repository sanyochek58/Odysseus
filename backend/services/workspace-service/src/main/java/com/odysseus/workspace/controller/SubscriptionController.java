package com.odysseus.workspace.controller;

import com.odysseus.workspace.config.RequiresMembership;
import com.odysseus.workspace.config.SubscriptionNotRequired;
import com.odysseus.workspace.dto.SubscriptionExtendRequest;
import com.odysseus.workspace.dto.SubscriptionResponse;
import com.odysseus.workspace.exception.InvalidRequestException;
import com.odysseus.workspace.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private static final int MAX_KEY_LENGTH = 128;

    private final SubscriptionService subscriptionService;

    @GetMapping("/current")
    @RequiresMembership
    public SubscriptionResponse current() {
        return subscriptionService.getCurrent();
    }

    /** Продление работает и для истёкшей подписки, поэтому блокировка записи снята. */
    @PostMapping("/current/extensions")
    @SubscriptionNotRequired
    @PreAuthorize("hasRole('OWNER')")
    public SubscriptionResponse extend(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubscriptionExtendRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Заголовок Idempotency-Key обязателен");
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestException("Idempotency-Key длиннее " + MAX_KEY_LENGTH + " символов");
        }
        return subscriptionService.extend(request.days(), idempotencyKey.strip());
    }
}
