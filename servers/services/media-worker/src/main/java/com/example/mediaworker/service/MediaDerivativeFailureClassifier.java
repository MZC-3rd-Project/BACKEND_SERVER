package com.example.mediaworker.service;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class MediaDerivativeFailureClassifier {

    public MediaDerivativeFailureDecision classify(Exception exception, int nextRetryCount, int maxRetryCount) {
        boolean retriable = isRetriable(exception);
        if (!retriable) {
            return new MediaDerivativeFailureDecision(false, MediaDerivativeFailureCode.NON_RETRIABLE_EXCEPTION);
        }
        if (nextRetryCount > maxRetryCount) {
            return new MediaDerivativeFailureDecision(false, MediaDerivativeFailureCode.MAX_RETRY_EXCEEDED);
        }
        return new MediaDerivativeFailureDecision(true, MediaDerivativeFailureCode.RETRIABLE_EXCEPTION);
    }

    private boolean isRetriable(Exception exception) {
        if (exception == null) {
            return true;
        }
        if (exception instanceof NonRetriableMediaProcessingException || exception instanceof IllegalArgumentException) {
            return false;
        }
        if (exception instanceof S3Exception s3Exception) {
            int statusCode = s3Exception.statusCode();
            return statusCode >= 500 || statusCode == 429 || statusCode == 408;
        }
        if (exception instanceof SdkClientException || exception instanceof SdkException) {
            return true;
        }
        return true;
    }
}
