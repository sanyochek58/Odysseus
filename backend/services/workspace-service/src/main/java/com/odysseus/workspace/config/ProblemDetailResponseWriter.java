package com.odysseus.workspace.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Пишет RFC 9457 ProblemDetail из фильтров и обработчиков безопасности, где нет MVC-обработки ошибок. */
@Component
@RequiredArgsConstructor
public class ProblemDetailResponseWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(jsonMapper.writeValueAsBytes(body));
    }
}
