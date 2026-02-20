package com.example.chat.service.audit;

import com.example.chat.entity.audit.ChatAuditEventType;
import com.example.chat.entity.audit.ChatAuditLog;
import com.example.chat.repository.ChatAuditLogRepository;
import com.example.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAuditService {

    private final ChatAuditLogRepository chatAuditLogRepository;

    public void logEvent(Long actorId,
                         Long roomId,
                         Long targetUserId,
                         ChatAuditEventType eventType,
                         Map<String, Object> payload) {
        if (eventType == null) {
            return;
        }
        try {
            String eventPayload = JsonUtils.toJson(payload == null ? Map.of() : payload);
            chatAuditLogRepository.save(ChatAuditLog.create(
                    actorId,
                    roomId,
                    targetUserId,
                    eventType,
                    eventPayload,
                    null
            ));
        } catch (Exception e) {
            log.warn("Failed to write chat audit log. eventType={}, roomId={}", eventType, roomId, e);
        }
    }
}
