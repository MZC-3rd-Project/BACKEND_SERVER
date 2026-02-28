package com.example.notification.service.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthEmailEventServiceTest {

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private AuthEmailEventService authEmailEventService;

    @Test
    void sendEmailConfirm_sendsMailWhenProviderSuccess() {
        when(emailSender.send(any())).thenReturn(EmailSendResult.success("SMTP", "msg-1"));

        authEmailEventService.sendEmailConfirm("evt-auth-1", "user@example.com", "ABC123");

        ArgumentCaptor<EmailSendCommand> captor = ArgumentCaptor.forClass(EmailSendCommand.class);
        verify(emailSender).send(captor.capture());
        EmailSendCommand command = captor.getValue();
        assertThat(command.getTo()).isEqualTo("user@example.com");
        assertThat(command.getSubject()).contains("이메일 인증 코드 안내");
        assertThat(command.getTextBody()).contains("ABC123");
        assertThat(command.getHtmlBody()).contains("돈오아");
        assertThat(command.getHtmlBody()).contains("ABC123");
    }

    @Test
    void sendEmailConfirm_throwsWhenProviderFailed() {
        when(emailSender.send(any())).thenReturn(EmailSendResult.failure("SMTP", "smtp unavailable"));

        assertThatThrownBy(() -> authEmailEventService.sendEmailConfirm("evt-auth-2", "user@example.com", "XYZ999"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mail send failed");
    }
}
