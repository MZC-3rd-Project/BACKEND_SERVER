package com.example.payment.exception;

import lombok.Getter;

@Getter
public class TossPaymentsApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public TossPaymentsApiException(int statusCode, String responseBody) {
        super("TossPayments API error: status=" + statusCode + ", body=" + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public TossPaymentsApiException(int statusCode, String responseBody, Throwable cause) {
        super("TossPayments API error: status=" + statusCode + ", body=" + responseBody, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
