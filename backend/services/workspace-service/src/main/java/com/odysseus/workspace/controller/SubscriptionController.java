package com.odysseus.workspace.controller;

import com.odysseus.workspace.config.SubscriptionNotRequired;
import com.odysseus.workspace.dto.SubscriptionExtendRequest;
import com.odysseus.workspace.dto.SubscriptionResponse;
import com.odysseus.workspace.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/current")
    public SubscriptionResponse current() {
        return subscriptionService.getCurrent();
    }

    /** Продление работает и для истёкшей подписки, поэтому блокировка записи снята. */
    @PostMapping("/current/extensions")
    @SubscriptionNotRequired
    @PreAuthorize("hasRole('OWNER')")
    public SubscriptionResponse extend(@Valid @RequestBody SubscriptionExtendRequest request) {
        return subscriptionService.extend(request.days());
    }
}
