package com.odysseus.workspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.odysseus.workspace.config.ClockConfig;
import com.odysseus.workspace.config.MemberRolePort;
import com.odysseus.workspace.config.PageSerializationConfig;
import com.odysseus.workspace.config.ProblemDetailResponseWriter;
import com.odysseus.workspace.config.SecurityConfig;
import com.odysseus.workspace.config.SubscriptionExpiryPort;
import com.odysseus.workspace.config.SubscriptionGuardWebConfig;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.exception.GlobalExceptionHandler;
import com.odysseus.workspace.service.MemberService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Проверка claim aud настоящим декодером Spring Boot по issuer-uri (без jwt()): ключ RSA генерируется в тесте,
 * локальный HTTP-сервер отдаёт OpenID-конфигурацию и JWKS, audiences берутся из application.yaml.
 */
@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, ProblemDetailResponseWriter.class, SubscriptionGuardWebConfig.class,
        GlobalExceptionHandler.class, ClockConfig.class, PageSerializationConfig.class})
class JwtAudienceValidationTest {

    private static final KeyPair KEYS = generateKeys();
    private static final HttpServer ISSUER_SERVER = startIssuer();
    private static final String ISSUER = "http://localhost:" + ISSUER_SERVER.getAddress().getPort() + "/realms/odysseus";
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final String USER_ID = "user-1";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private SubscriptionExpiryPort subscriptionExpiryPort;
    @MockitoBean
    private MemberRolePort memberRolePort;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_ISSUER_URI", () -> ISSUER);
    }

    @AfterAll
    static void stopIssuer() {
        ISSUER_SERVER.stop(0);
    }

    @BeforeEach
    void setUp() {
        when(subscriptionExpiryPort.findExpiresAt(any())).thenReturn(Optional.of(Instant.now().plusSeconds(3600)));
        when(memberRolePort.findRole(WORKSPACE, USER_ID)).thenReturn(Optional.of(WorkspaceRole.MEMBER));
        when(memberService.list(any())).thenReturn(Page.empty());
    }

    @Test
    @DisplayName("Токен с aud=odysseus-api принимается, 200")
    void request_validAudience_returns200() throws Exception {
        mvc.perform(get("/api/v1/members").header(HttpHeaders.AUTHORIZATION, bearer(List.of("odysseus-api"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Токен без aud, 401 ProblemDetail")
    void request_noAudience_returns401() throws Exception {
        mvc.perform(get("/api/v1/members").header(HttpHeaders.AUTHORIZATION, bearer(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("Токен с чужим aud, 401")
    void request_otherAudience_returns401() throws Exception {
        mvc.perform(get("/api/v1/members").header(HttpHeaders.AUTHORIZATION, bearer(List.of("account"))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(memberService);
    }

    private static String bearer(List<String> audience) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(USER_ID)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("organization", Map.of("acme", Map.of("id", WORKSPACE.toString())));
        if (audience != null) {
            claims.audience(audience);
        }
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) KEYS.getPublic()).privateKey((RSAPrivateKey) KEYS.getPrivate()).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
        return "Bearer " + token;
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Минимальный issuer: OpenID-конфигурация и JWKS с публичным ключом теста. */
    private static HttpServer startIssuer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            String base = "http://localhost:" + server.getAddress().getPort() + "/realms/odysseus";
            String jwks = new JWKSet(new RSAKey.Builder((RSAPublicKey) KEYS.getPublic()).build()).toString();
            String config = "{\"issuer\":\"" + base + "\",\"jwks_uri\":\"" + base + "/protocol/openid-connect/certs\"}";
            server.createContext("/realms/odysseus/.well-known/openid-configuration", exchange -> respond(exchange, config));
            server.createContext("/realms/odysseus/protocol/openid-connect/certs", exchange -> respond(exchange, jwks));
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
