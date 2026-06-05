package com.gpm.payroll.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(buildErrorJson(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Forbidden: you do not have permission to access this resource"
        ));
    }

    private String buildErrorJson(int status, String error, String message) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"timestamp\":\"" + ts + "\"}";
    }
}
