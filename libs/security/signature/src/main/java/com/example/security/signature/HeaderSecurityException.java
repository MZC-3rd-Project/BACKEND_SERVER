package com.example.security.signature;

import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;

public class HeaderSecurityException extends TechnicalException {

    public HeaderSecurityException(String message) {
        super(CommonErrorCode.UNAUTHORIZED, message);
    }

    public HeaderSecurityException(String message, Throwable cause) {
        super(CommonErrorCode.UNAUTHORIZED, message, cause);
    }
}
