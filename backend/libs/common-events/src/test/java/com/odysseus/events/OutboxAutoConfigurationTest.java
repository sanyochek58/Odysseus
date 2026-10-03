package com.odysseus.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.odysseus.events.outbox.OutboxProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OutboxAutoConfigurationTest {

    @Configuration
    @EnableConfigurationProperties(OutboxProperties.class)
    static class Props {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Props.class);

    @Test
    @DisplayName("свойства: значения по умолчанию")
    void properties_defaults() {
        runner.run(ctx -> {
            OutboxProperties p = ctx.getBean(OutboxProperties.class);
            assertThat(p.batchSize()).isEqualTo(100);
            assertThat(p.pollIntervalMs()).isEqualTo(1000);
            assertThat(p.retry().maxAttempts()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("свойства: odysseus.outbox.* переопределяются")
    void properties_overridden() {
        runner.withPropertyValues("odysseus.outbox.batch-size=7", "odysseus.outbox.retry.max-attempts=5")
                .run(ctx -> {
                    OutboxProperties p = ctx.getBean(OutboxProperties.class);
                    assertThat(p.batchSize()).isEqualTo(7);
                    assertThat(p.retry().maxAttempts()).isEqualTo(5);
                });
    }

    @Test
    @DisplayName("backOff: параметры retry переносятся в ExponentialBackOff")
    void backOff_fromProperties() {
        var b = OutboxAutoConfiguration.backOff(new OutboxProperties.Retry(4, 500, 3.0, 9000));
        assertThat(b.getInitialInterval()).isEqualTo(500);
        assertThat(b.getMaxAttempts()).isEqualTo(4);
        assertThat(b.getMaxInterval()).isEqualTo(9000);
    }
}
