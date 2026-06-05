package com.gpm.payroll.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(buildErrorJson(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Unauthorized: missing or invalid access token"
        ));
    }

    private String buildErrorJson(int status, String error, String message) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"timestamp\":\"" + ts + "\"}";
    }
}
