package com.odysseus.workspace.config;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Подключает блокировку записи по подписке ко всем обработчикам. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class SubscriptionGuardWebConfig implements WebMvcConfigurer {

    private final SubscriptionExpiryPort subscriptionExpiryPort;
    private final ProblemDetailResponseWriter problemWriter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
                new SubscriptionWriteGuardInterceptor(subscriptionExpiryPort, problemWriter, Clock.systemUTC()));
    }
}
