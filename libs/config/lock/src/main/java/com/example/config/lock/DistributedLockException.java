package com.example.config.lock;

import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;

public class DistributedLockException extends TechnicalException {

    public DistributedLockException(String message) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message);
    }

    public DistributedLockException(String message, Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}
