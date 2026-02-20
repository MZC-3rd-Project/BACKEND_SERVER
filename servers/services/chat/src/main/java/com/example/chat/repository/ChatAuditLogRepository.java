package com.example.chat.repository;

import com.example.chat.entity.audit.ChatAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatAuditLogRepository extends JpaRepository<ChatAuditLog, Long> {

    List<ChatAuditLog> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);
}
