package com.example.security.crypto.encryption;

import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;

public class EncryptionException extends TechnicalException {

    public EncryptionException(String message) {
        super(CommonErrorCode.INTERNAL_ERROR, message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(CommonErrorCode.INTERNAL_ERROR, message, cause);
    }
}
