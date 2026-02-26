package com.example.notification.dto.command.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CreateNotificationRequest {

    @NotNull(message = "수신자 ID는 필수입니다")
    private Long recipientId;

    private Long actorId;

    @NotBlank(message = "알림 타입은 필수입니다")
    @Size(max = 40, message = "알림 타입은 40자를 초과할 수 없습니다")
    private String type;

    private List<String> channels;

    @Size(max = 10, message = "locale은 10자를 초과할 수 없습니다")
    private String locale;

    @Email(message = "유효한 이메일 형식이어야 합니다")
    @Size(max = 320, message = "이메일은 320자를 초과할 수 없습니다")
    private String emailTo;

    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    private String title;
    @Size(max = 2000, message = "메시지는 2000자를 초과할 수 없습니다")
    private String message;

    @Size(max = 50, message = "referenceType은 50자를 초과할 수 없습니다")
    private String referenceType;
    @Size(max = 100, message = "referenceId는 100자를 초과할 수 없습니다")
    private String referenceId;

    @Size(max = 100, message = "externalEventId는 100자를 초과할 수 없습니다")
    private String externalEventId;
    @Size(max = 160, message = "dedupeKey는 160자를 초과할 수 없습니다")
    private String dedupeKey;

    private Map<String, Object> variables;
}
