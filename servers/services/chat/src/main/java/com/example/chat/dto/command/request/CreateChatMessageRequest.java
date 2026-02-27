package com.example.chat.dto.command.request;

import com.example.chat.entity.message.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CreateChatMessageRequest {

    @Size(max = 100, message = "clientMessageId는 100자를 초과할 수 없습니다")
    private String clientMessageId;

    @NotNull(message = "messageType은 필수입니다")
    private ChatMessageType messageType;

    @NotBlank(message = "content는 필수입니다")
    @Size(max = 4000, message = "content는 4000자를 초과할 수 없습니다")
    private String content;

    private Map<String, Object> metadata;
}
