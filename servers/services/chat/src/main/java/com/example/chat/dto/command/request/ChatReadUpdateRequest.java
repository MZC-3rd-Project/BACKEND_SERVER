package com.example.chat.dto.command.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ChatReadUpdateRequest {

    @NotNull(message = "lastReadMessageId는 필수입니다")
    private Long lastReadMessageId;
}
