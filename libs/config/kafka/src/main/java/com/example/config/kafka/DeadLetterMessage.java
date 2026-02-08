package com.example.config.kafka;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeadLetterMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "partition_num")
    private Integer partition;

    @Column(name = "offset_num")
    private Long offset;

    @Column(name = "key_value")
    private String key;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static DeadLetterMessage create(String topic, Integer partition, Long offset,
                                            String key, String payload, String errorMessage) {
        DeadLetterMessage dlm = new DeadLetterMessage();
        dlm.topic = topic;
        dlm.partition = partition;
        dlm.offset = offset;
        dlm.key = key;
        dlm.payload = payload;
        dlm.errorMessage = errorMessage;
        dlm.retryCount = 0;
        dlm.createdAt = LocalDateTime.now();
        return dlm;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}
