package com.utochkin.paymentservice.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.paymentservice.requests.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpStatus.FORBIDDEN.value());

        ErrorResponse errorResponse = new ErrorResponse(accessDeniedException.getMessage());
        String json = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(json);
    }
}
