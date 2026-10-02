package com.odysseus.workspace.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class ApplicationStartupIT extends IntegrationTestBase {

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Test
    @DisplayName("контекст: стартует на Testcontainers при ddl-auto=validate, Liquibase создал все таблицы")
    void context_startsWithValidate_liquibaseAppliedAllTables() {
        assertThat(ddlAuto).isEqualTo("validate");

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("workspace", "member", "invitation", "subscription", "outbox",
                "processed_events", "subscription_extension_key", "databasechangelog");

        Integer applied = jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class);
        assertThat(applied).isPositive();
    }

    @Test
    @DisplayName("health: открыт без токена")
    void health_withoutToken_isUp() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
}
