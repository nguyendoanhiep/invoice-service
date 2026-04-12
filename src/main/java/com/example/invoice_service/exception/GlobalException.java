package com.example.invoice_service.exception;

import com.example.invoice_service.entity.response.ApiResponse;
import com.example.invoice_service.utils.ErrorLogUtil;
import org.springframework.web.bind.annotation.ControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Enumeration;

@Slf4j
@ControllerAdvice
public class GlobalException {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllException(
            Exception ex,
            HttpServletRequest request
    ) {

        // ===== LOG FULL INFORMATION =====
        log.error(
                """
                ===================== EXCEPTION =====================
                Time       : {}
                Method     : {}
                URI        : {}
                Client IP : {}
                Params     : {}
                Exception  : {}
                Message    : {}
                =====================================================
                """,
                LocalDateTime.now(),
                request.getMethod(),
                request.getRequestURI(),
                getClientIp(request),
                getRequestParams(request),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        // ===== RESPONSE CHO CLIENT =====
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.builder().code("500").message("Lỗi hệ thống").data(false).build());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException e) {
        ErrorLogUtil.log(e);
        return ResponseEntity
                .ok(ApiResponse.builder().data(false).code(e.getCode()).message(e.getMessage()).build());
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    private String getRequestParams(HttpServletRequest request) {
        StringBuilder params = new StringBuilder();
        Enumeration<String> paramNames = request.getParameterNames();

        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            params.append(name)
                    .append("=")
                    .append(request.getParameter(name))
                    .append(" ");
        }
        return params.toString().trim();
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> ignoreStatic(NoResourceFoundException ex,
                                             HttpServletRequest request) {

        String uri = request.getRequestURI();

        if (uri.startsWith("/.well-known")
                || uri.equals("/favicon.ico")
                || uri.equals("/robots.txt")) {
            return ResponseEntity.notFound().build();
        }

        log.warn("Static resource not found: {}", uri);
        return ResponseEntity.notFound().build();
    }


}
