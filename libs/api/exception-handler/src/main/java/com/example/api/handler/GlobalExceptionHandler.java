package com.example.api.handler;

import com.example.api.response.ApiResponse;
import com.example.core.exception.ApplicationException;
import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e,
                                                                    HttpServletRequest request,
                                                                    HttpServletResponse response) {
        log.warn("[Business] code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return buildErrorResponse(
                e.getErrorCode().getHttpStatus(),
                ApiResponse.error(e.getErrorCode(), e.getMessage()),
                request,
                response
        );
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ApiResponse<Void>> handleTechnicalException(TechnicalException e,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response) {
        log.error("[Technical] code={}, message={}", e.getErrorCode().getCode(), e.getMessage(), e);
        return buildErrorResponse(
                e.getErrorCode().getHttpStatus(),
                ApiResponse.error(e.getErrorCode(), e.getErrorCode().getMessage()),
                request,
                response
        );
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(ApplicationException e,
                                                                       HttpServletRequest request,
                                                                       HttpServletResponse response) {
        log.error("[Application] code={}, message={}", e.getErrorCode().getCode(), e.getMessage(), e);
        return buildErrorResponse(
                e.getErrorCode().getHttpStatus(),
                ApiResponse.error(e.getErrorCode(), e.getMessage()),
                request,
                response
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                          HttpServletRequest request,
                                                                          HttpServletResponse response) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[Validation] {}", message);
        return buildErrorResponse(
                CommonErrorCode.VALIDATION_ERROR.getHttpStatus(),
                ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, message),
                request,
                response
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e,
                                                                      HttpServletRequest request,
                                                                      HttpServletResponse response) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("[Validation] {}", message);
        return buildErrorResponse(
                CommonErrorCode.VALIDATION_ERROR.getHttpStatus(),
                ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, message),
                request,
                response
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                          HttpServletRequest request,
                                                                          HttpServletResponse response) {
        log.warn("[Request] 잘못된 요청 본문: {}", e.getMessage());
        return buildErrorResponse(
                CommonErrorCode.INVALID_REQUEST.getHttpStatus(),
                ApiResponse.error(CommonErrorCode.INVALID_REQUEST, "잘못된 요청 본문입니다"),
                request,
                response
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(MissingRequestHeaderException e,
                                                                       HttpServletRequest request,
                                                                       HttpServletResponse response) {
        log.warn("[Request] 필수 헤더 누락: {}", e.getHeaderName());
        return buildErrorResponse(
                CommonErrorCode.MISSING_PARAMETER.getHttpStatus(),
                ApiResponse.error(CommonErrorCode.MISSING_PARAMETER, "필수 헤더가 누락되었습니다: " + e.getHeaderName()),
                request,
                response
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                      HttpServletRequest request,
                                                                      HttpServletResponse response) {
        log.warn("[Request] 지원하지 않는 HTTP 메서드: {}", e.getMethod());
        return buildErrorResponse(
                CommonErrorCode.METHOD_NOT_ALLOWED.getHttpStatus(),
                ApiResponse.error(CommonErrorCode.METHOD_NOT_ALLOWED),
                request,
                response
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response) {
        log.warn("[Request] 잘못된 인자: {}", e.getMessage());
        return buildErrorResponse(
                CommonErrorCode.INVALID_REQUEST.getHttpStatus(),
                ApiResponse.error(CommonErrorCode.INVALID_REQUEST, e.getMessage()),
                request,
                response
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        log.error("[Unhandled] {}", e.getMessage(), e);
        return buildErrorResponse(
                CommonErrorCode.INTERNAL_ERROR.getHttpStatus(),
                ApiResponse.error(CommonErrorCode.INTERNAL_ERROR),
                request,
                response
        );
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(HttpStatus status,
                                                                 ApiResponse<Void> body,
                                                                 HttpServletRequest request,
                                                                 HttpServletResponse response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (isSseRequest(request, response)) {
            builder.contentType(MediaType.APPLICATION_JSON);
        }
        return builder.body(body);
    }

    private boolean isSseRequest(HttpServletRequest request, HttpServletResponse response) {
        return isSseMediaType(response.getContentType())
                || hasSseProducibleMediaType(request)
                || acceptsSse(request);
    }

    private boolean hasSseProducibleMediaType(HttpServletRequest request) {
        Object producibleMediaTypes = request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
        if (!(producibleMediaTypes instanceof Set<?> mediaTypes)) {
            return false;
        }
        return mediaTypes.stream()
                .filter(MediaType.class::isInstance)
                .map(MediaType.class::cast)
                .anyMatch(MediaType.TEXT_EVENT_STREAM::isCompatibleWith);
    }

    private boolean acceptsSse(HttpServletRequest request) {
        String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return false;
        }
        try {
            return MediaType.parseMediaTypes(acceptHeader).stream()
                    .anyMatch(MediaType.TEXT_EVENT_STREAM::isCompatibleWith);
        } catch (InvalidMediaTypeException ignored) {
            return false;
        }
    }

    private boolean isSseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            return MediaType.TEXT_EVENT_STREAM.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException ignored) {
            return false;
        }
    }
}
