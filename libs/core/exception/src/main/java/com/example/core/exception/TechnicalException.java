package com.example.core.exception;

public class TechnicalException extends ApplicationException {

    public TechnicalException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TechnicalException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TechnicalException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public TechnicalException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    @Override
    public String getExceptionType() {
        return "TECHNICAL";
    }
}
