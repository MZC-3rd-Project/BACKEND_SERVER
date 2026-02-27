package com.example.mediaworker.service;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;

class MediaDerivativeFailureClassifierTest {

    private final MediaDerivativeFailureClassifier classifier = new MediaDerivativeFailureClassifier();

    @Test
    void classify_nonRetriableException_returnsNonRetriableDecision() {
        MediaDerivativeFailureDecision decision = classifier.classify(
                new NonRetriableMediaProcessingException("broken image"),
                1,
                3
        );

        assertThat(decision.retriable()).isFalse();
        assertThat(decision.failureCode()).isEqualTo(MediaDerivativeFailureCode.NON_RETRIABLE_EXCEPTION);
    }

    @Test
    void classify_retriableWithinLimit_returnsRetriableDecision() {
        MediaDerivativeFailureDecision decision = classifier.classify(
                new RuntimeException("temporary network"),
                1,
                3
        );

        assertThat(decision.retriable()).isTrue();
        assertThat(decision.failureCode()).isEqualTo(MediaDerivativeFailureCode.RETRIABLE_EXCEPTION);
    }

    @Test
    void classify_retriableOverLimit_returnsMaxRetryDecision() {
        MediaDerivativeFailureDecision decision = classifier.classify(
                new RuntimeException("temporary network"),
                4,
                3
        );

        assertThat(decision.retriable()).isFalse();
        assertThat(decision.failureCode()).isEqualTo(MediaDerivativeFailureCode.MAX_RETRY_EXCEEDED);
    }

    @Test
    void classify_s3ServerError_isRetriable() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
                .message("server error")
                .statusCode(503)
                .build();

        MediaDerivativeFailureDecision decision = classifier.classify(s3Exception, 1, 3);

        assertThat(decision.retriable()).isTrue();
    }
}
