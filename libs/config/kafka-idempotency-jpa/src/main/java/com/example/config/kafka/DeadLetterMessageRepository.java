package com.example.config.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterMessageRepository extends JpaRepository<DeadLetterMessage, Long> {

    List<DeadLetterMessage> findByTopicOrderByCreatedAtAsc(String topic);
}
