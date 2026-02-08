package com.example.core.exception;

import org.springframework.http.HttpStatus;

public sealed interface ErrorCode permits CommonErrorCode, DomainErrorCode {

    String getCode();

    String getMessage();

    HttpStatus getHttpStatus();
}
