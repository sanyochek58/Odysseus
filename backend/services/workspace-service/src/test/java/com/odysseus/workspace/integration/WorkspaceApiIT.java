package com.odysseus.workspace.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Сквозные сценарии API на реальной БД: успех, 400, 403, 404, 409, изоляция A/B, подписка, идемпотентность. */
class WorkspaceApiIT extends IntegrationTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private UUID a;
    private UUID b;
    private String ownerA;
    private String ownerB;

    @BeforeEach
    void setUp() throws Exception {
        a = UUID.randomUUID();
        b = UUID.randomUUID();
        ownerA = "owner-a-" + a;
        ownerB = "owner-b-" + b;
        createWorkspace(a, ownerA, "Acme A").andExpect(status().isCreated());
        createWorkspace(b, ownerB, "Beta B").andExpect(status().isCreated());
    }

    private ResultActions createWorkspace(UUID ws, String user, String name) throws Exception {
        return mvc.perform(post("/api/v1/workspaces").with(token(ws, user)).contentType(JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private RequestPostProcessor asOwnerA() {
        return token(a, ownerA);
    }

    private RequestPostProcessor asOwnerB() {
        return token(b, ownerB);
    }

    private ResultActions extend(RequestPostProcessor who, String key, int days) throws Exception {
        var request = post("/api/v1/subscriptions/current/extensions").with(who).contentType(JSON)
                .content("{\"days\":" + days + "}");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    private String memberIdOf(UUID ws, String userId) {
        return jdbc.queryForObject("SELECT id FROM member WHERE workspace_id = ? AND user_id = ?", String.class, ws, userId);
    }

    // ---- успех, 400, 409 ----

    @Test
    @DisplayName("POST /workspaces: создатель OWNER, пробная подписка на 30 дней")
    void createWorkspace_success_ownerAndTrialCreated() throws Exception {
        mvc.perform(get("/api/v1/workspaces/{id}", a).with(asOwnerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Acme A")));
        mvc.perform(get("/api/v1/members").with(asOwnerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].role", is("OWNER")));
        mvc.perform(get("/api/v1/subscriptions/current").with(asOwnerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @DisplayName("POST /workspaces: пустое имя, 400 ProblemDetail")
    void createWorkspace_blankName_returns400() throws Exception {
        mvc.perform(post("/api/v1/workspaces").with(token(UUID.randomUUID(), "u1")).contentType(JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("POST /workspaces: повторная регистрация, 409")
    void createWorkspace_twice_returns409() throws Exception {
        createWorkspace(a, ownerA, "Again").andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("POST /invitations: невалидный email, 400; дубликат PENDING, 409")
    void invite_invalidAndDuplicate_returns400And409() throws Exception {
        mvc.perform(post("/api/v1/invitations").with(asOwnerA()).contentType(JSON)
                        .content("{\"email\":\"not-an-email\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest());
        String body = "{\"email\":\"New@Acme.io\",\"role\":\"MEMBER\"}";
        mvc.perform(post("/api/v1/invitations").with(asOwnerA()).contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("new@acme.io")));
        mvc.perform(post("/api/v1/invitations").with(asOwnerA()).contentType(JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /members/{id}/role: понижение единственного OWNER, 409")
    void changeRole_lastOwner_returns409() throws Exception {
        mvc.perform(put("/api/v1/members/{id}/role", memberIdOf(a, ownerA)).with(asOwnerA()).contentType(JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict());
    }

    // ---- 403 ----

    @Test
    @DisplayName("права: MEMBER не приглашает (403), не-участник и токен без организации получают 403")
    void protectedOperations_withoutRights_return403() throws Exception {
        insertMember(a, "plain-member", "MEMBER");
        mvc.perform(post("/api/v1/invitations").with(token(a, "plain-member")).contentType(JSON)
                        .content("{\"email\":\"x@acme.io\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/members").with(token(a, "stranger"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members").with(tokenWithoutOrganization("nobody"))).andExpect(status().isForbidden());
        extend(token(a, "plain-member"), "k-" + UUID.randomUUID(), 5).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /members/{id}: ADMIN удаляет OWNER, 403; OWNER остаётся")
    void deleteMember_adminDeletesOwner_returns403() throws Exception {
        insertMember(a, "admin-a", "ADMIN");
        mvc.perform(delete("/api/v1/members/{id}", memberIdOf(a, ownerA)).with(token(a, "admin-a")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE workspace_id = ? AND user_id = ?",
                Integer.class, a, ownerA)).isEqualTo(1);
    }

    @Test
    @DisplayName("без токена: 401")
    void anyEndpoint_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/members")).andExpect(status().isUnauthorized());
    }

    // ---- изоляция A/B ----

    @Test
    @DisplayName("изоляция: A не видит workspace B и участников B, списки содержат только свои данные")
    void isolation_userA_doesNotSeeDataOfB() throws Exception {
        mvc.perform(get("/api/v1/workspaces/{id}", b).with(asOwnerA())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/members/{id}", memberIdOf(b, ownerB)).with(asOwnerA())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/members").with(asOwnerA()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId", is(ownerA)));
        mvc.perform(get("/api/v1/workspaces").with(asOwnerA()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Acme A")));
    }

    @Test
    @DisplayName("изоляция: A не меняет и не удаляет данные B (404), данные B не изменились")
    void isolation_userA_cannotModifyDataOfB() throws Exception {
        String memberB = memberIdOf(b, ownerB);
        mvc.perform(put("/api/v1/workspaces/{id}", b).with(asOwnerA()).contentType(JSON).content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/members/{id}/role", memberB).with(asOwnerA()).contentType(JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/members/{id}", memberB).with(asOwnerA())).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/invitations").with(asOwnerB()).contentType(JSON)
                .content("{\"email\":\"guest@beta.io\",\"role\":\"MEMBER\"}")).andExpect(status().isCreated());
        String invitationB = jdbc.queryForObject("SELECT id FROM invitation WHERE workspace_id = ?", String.class, b);
        mvc.perform(delete("/api/v1/invitations/{id}", invitationB).with(asOwnerA())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/invitations/{id}/accept", invitationB).with(token(a, "guest", "guest@beta.io", true)))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT name FROM workspace WHERE id = ?", String.class, b)).isEqualTo("Beta B");
        assertThat(jdbc.queryForObject("SELECT role FROM member WHERE id = ?::uuid", String.class, memberB)).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("SELECT status FROM invitation WHERE id = ?::uuid", String.class, invitationB))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("изоляция: тот же email можно пригласить в оба workspace, приглашения не пересекаются")
    void isolation_sameEmailInBothWorkspaces_isIndependent() throws Exception {
        String body = "{\"email\":\"shared@mail.io\",\"role\":\"MEMBER\"}";
        mvc.perform(post("/api/v1/invitations").with(asOwnerA()).contentType(JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/invitations").with(asOwnerB()).contentType(JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/invitations").with(asOwnerA())).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("изоляция: подписка и продление A не затрагивают B")
    void isolation_extendA_doesNotChangeSubscriptionOfB() throws Exception {
        String before = jdbc.queryForObject("SELECT expires_at::text FROM subscription WHERE workspace_id = ?", String.class, b);
        extend(asOwnerA(), "k-" + UUID.randomUUID(), 30).andExpect(status().isOk());
        String after = jdbc.queryForObject("SELECT expires_at::text FROM subscription WHERE workspace_id = ?", String.class, b);
        assertThat(after).isEqualTo(before);
    }

    // ---- идемпотентность ----

    @Test
    @DisplayName("продление: повтор того же Idempotency-Key, 409 и второго продления нет")
    void extend_sameIdempotencyKey_returns409WithoutSecondExtension() throws Exception {
        String key = "key-" + UUID.randomUUID();
        extend(asOwnerA(), key, 10).andExpect(status().isOk());
        String afterFirst = jdbc.queryForObject("SELECT expires_at::text FROM subscription WHERE workspace_id = ?", String.class, a);

        extend(asOwnerA(), key, 10).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        String afterSecond = jdbc.queryForObject("SELECT expires_at::text FROM subscription WHERE workspace_id = ?", String.class, a);
        assertThat(afterSecond).isEqualTo(afterFirst);
        Integer events = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE workspace_id = ?", Integer.class, a);
        assertThat(events).isEqualTo(2); // создание + одно продление
    }

    @Test
    @DisplayName("продление: тот же ключ в другом workspace допустим, ключ уникален в рамках workspace")
    void extend_sameKeyInOtherWorkspace_isAllowed() throws Exception {
        String key = "shared-" + UUID.randomUUID();
        extend(asOwnerA(), key, 5).andExpect(status().isOk());
        extend(asOwnerB(), key, 5).andExpect(status().isOk());
    }

    @Test
    @DisplayName("продление: нет ключа, пустой ключ, слишком длинный ключ или days вне диапазона, 400")
    void extend_invalidInput_returns400() throws Exception {
        extend(asOwnerA(), null, 5).andExpect(status().isBadRequest());
        extend(asOwnerA(), " ", 5).andExpect(status().isBadRequest());
        extend(asOwnerA(), "k".repeat(129), 5).andExpect(status().isBadRequest());
        extend(asOwnerA(), "k-" + UUID.randomUUID(), 0).andExpect(status().isBadRequest());
        extend(asOwnerA(), "k-" + UUID.randomUUID(), 367).andExpect(status().isBadRequest());
    }

    // ---- истёкшая подписка ----

    @Test
    @DisplayName("после expiresAt запись даёт 403 ProblemDetail, чтение работает, продление разрешено и возобновляет запись")
    void write_afterSubscriptionExpired_returns403() throws Exception {
        jdbc.update("UPDATE subscription SET expires_at = now() - interval '1 day' WHERE workspace_id = ?", a);

        mvc.perform(post("/api/v1/invitations").with(asOwnerA()).contentType(JSON)
                        .content("{\"email\":\"late@acme.io\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status", is(403)));
        mvc.perform(put("/api/v1/workspaces/{id}", a).with(asOwnerA()).contentType(JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members").with(asOwnerA())).andExpect(status().isOk());
        // подписка B не затронута
        mvc.perform(put("/api/v1/workspaces/{id}", b).with(asOwnerB()).contentType(JSON).content("{\"name\":\"Beta 2\"}"))
                .andExpect(status().isOk());

        extend(asOwnerA(), "renew-" + UUID.randomUUID(), 30).andExpect(status().isOk());
        mvc.perform(put("/api/v1/workspaces/{id}", a).with(asOwnerA()).contentType(JSON).content("{\"name\":\"Renewed\"}"))
                .andExpect(status().isOk());
    }
}
