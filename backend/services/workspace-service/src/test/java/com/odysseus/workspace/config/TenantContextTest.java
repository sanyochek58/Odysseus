package com.odysseus.workspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    private final UUID workspaceA = UUID.randomUUID();
    private final UUID workspaceB = UUID.randomUUID();

    @Test
    @DisplayName("runAs: внутри виден workspace, после выхода контекст очищен")
    void runAs_validWorkspace_setsAndClearsContext() {
        AtomicReference<Optional<UUID>> inside = new AtomicReference<>();

        TenantContext.runAs(workspaceA, () -> inside.set(TenantContext.currentWorkspaceId()));

        assertThat(inside.get()).contains(workspaceA);
        assertThat(TenantContext.currentWorkspaceId()).isEmpty();
        assertThat(TenantContext.currentIdentifier()).isNull();
    }

    @Test
    @DisplayName("runAs: контекст очищается и при исключении")
    void runAs_actionThrows_clearsContext() {
        assertThatThrownBy(() -> TenantContext.runAs(workspaceA, () -> {
            throw new IllegalStateException("сбой");
        })).isInstanceOf(IllegalStateException.class).hasMessage("сбой");

        assertThat(TenantContext.currentIdentifier()).isNull();
    }

    @Test
    @DisplayName("callAs: возвращает результат действия")
    void callAs_validWorkspace_returnsResult() {
        UUID result = TenantContext.callAs(workspaceA, TenantContext::requireWorkspaceId);

        assertThat(result).isEqualTo(workspaceA);
    }

    @Test
    @DisplayName("runAs: смена тенанта внутри чужого контекста запрещена")
    void runAs_nestedDifferentWorkspace_throws() {
        TenantContext.runAs(workspaceA, () -> {
            assertThatThrownBy(() -> TenantContext.runAs(workspaceB, () -> { }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(TenantContext.currentWorkspaceId()).contains(workspaceA);
        });
    }

    @Test
    @DisplayName("runAs: повтор того же workspace допустим и не сбрасывает внешний контекст")
    void runAs_nestedSameWorkspace_keepsOuterContext() {
        TenantContext.runAs(workspaceA, () -> {
            TenantContext.runAs(workspaceA, () -> assertThat(TenantContext.currentWorkspaceId()).contains(workspaceA));
            assertThat(TenantContext.currentWorkspaceId()).contains(workspaceA);
        });
    }

    @Test
    @DisplayName("runAs: null и системный идентификатор отклоняются")
    void runAs_nullOrSystemId_throws() {
        assertThatThrownBy(() -> TenantContext.runAs(null, () -> { }))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TenantContext.runAs(TenantContext.SYSTEM, () -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("runAsSystem: системный контекст не даёт workspace")
    void runAsSystem_noWorkspace_isSystemWithoutWorkspaceId() {
        TenantContext.runAsSystem(() -> {
            assertThat(TenantContext.isSystem()).isTrue();
            assertThat(TenantContext.currentWorkspaceId()).isEmpty();
            assertThatThrownBy(TenantContext::requireWorkspaceId).isInstanceOf(IllegalStateException.class);
        });
        assertThat(TenantContext.isSystem()).isFalse();
    }

    @Test
    @DisplayName("runAsSystem: внутри контекста workspace запрещён")
    void runAsSystem_insideWorkspaceContext_throws() {
        TenantContext.runAs(workspaceA, () -> assertThatThrownBy(() -> TenantContext.runAsSystem(() -> { }))
                .isInstanceOf(IllegalStateException.class));
    }

    @Test
    @DisplayName("requireWorkspaceId: вне контекста исключение")
    void requireWorkspaceId_noContext_throws() {
        assertThatThrownBy(TenantContext::requireWorkspaceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Контекст не виден из другого потока")
    void runAs_otherThread_doesNotSeeContext() throws InterruptedException {
        AtomicReference<Optional<UUID>> seen = new AtomicReference<>();

        TenantContext.runAs(workspaceA, () -> {
            Thread thread = Thread.ofVirtual().start(() -> seen.set(TenantContext.currentWorkspaceId()));
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(seen.get()).isEmpty();
    }
}
