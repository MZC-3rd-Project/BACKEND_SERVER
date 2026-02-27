package com.example.chat.dto.command.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatReadUpdateResponse {

    private Long roomId;
    private Long lastReadMessageId;
}
