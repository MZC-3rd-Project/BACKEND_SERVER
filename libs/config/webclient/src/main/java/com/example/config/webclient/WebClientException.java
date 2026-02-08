package com.example.config.webclient;

public class WebClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public WebClientException(int statusCode, String responseBody) {
        super("WebClient error with status code: " + statusCode + ", body: " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public WebClientException(int statusCode, String responseBody, Throwable cause) {
        super("WebClient error with status code: " + statusCode + ", body: " + responseBody, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
