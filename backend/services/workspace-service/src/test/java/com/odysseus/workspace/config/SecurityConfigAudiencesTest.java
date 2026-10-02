package com.odysseus.workspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecurityConfigAudiencesTest {

    @Test
    @DisplayName("requireAudiences: значение задано, возвращается список")
    void requireAudiences_configured_returnsList() {
        MockEnvironment env = new MockEnvironment().withProperty(SecurityConfig.AUDIENCES_PROPERTY + "[0]", "odysseus-api");

        assertThat(SecurityConfig.requireAudiences(env)).containsExactly("odysseus-api");
    }

    @Test
    @DisplayName("requireAudiences: не задано, старт падает")
    void requireAudiences_missing_throws() {
        assertThatThrownBy(() -> SecurityConfig.requireAudiences(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("requireAudiences: пустое значение, старт падает")
    void requireAudiences_blank_throws() {
        MockEnvironment env = new MockEnvironment().withProperty(SecurityConfig.AUDIENCES_PROPERTY + "[0]", " ");

        assertThatThrownBy(() -> SecurityConfig.requireAudiences(env)).isInstanceOf(IllegalStateException.class);
    }
}
