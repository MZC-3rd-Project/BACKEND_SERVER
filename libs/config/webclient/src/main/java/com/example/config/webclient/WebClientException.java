package com.example.config.webclient;

import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import lombok.Getter;

@Getter
public class WebClientException extends TechnicalException {

    private final int statusCode;
    private final String responseBody;

    public WebClientException(int statusCode, String responseBody) {
        super(CommonErrorCode.EXTERNAL_API_ERROR,
                "외부 API 호출 실패 - status: " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public WebClientException(int statusCode, String responseBody, Throwable cause) {
        super(CommonErrorCode.EXTERNAL_API_ERROR,
                "외부 API 호출 실패 - status: " + statusCode, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
