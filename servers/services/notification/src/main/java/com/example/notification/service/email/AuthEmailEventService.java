package com.example.notification.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEmailEventService {

    private static final String EMAIL_CONFIRM_SUBJECT = "[돈오아] 이메일 인증 코드 안내";
    private static final String EMAIL_CONFIRM_TEXT_TEMPLATE = """
            안녕하세요, 돈오아입니다.

            요청하신 이메일 인증 코드를 안내드립니다.

            인증 코드: %s

            보안을 위해 본 코드는 일정 시간 후 만료됩니다.
            코드 요청을 하지 않으셨다면 본 메일을 무시해 주세요.
            """;
    private static final String EMAIL_CONFIRM_HTML_TEMPLATE = """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>돈오아 이메일 인증</title>
            </head>
            <body style="margin:0; padding:24px; background:#f2f6f9; font-family:'Segoe UI','Noto Sans KR',sans-serif; color:#102a43;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:640px; margin:0 auto; border-collapse:collapse;">
                <tr>
                  <td style="padding:28px; border-radius:18px; background:linear-gradient(135deg,#0a7ea4 0%%,#127fbf 55%%,#2f9d9c 100%%); color:#ffffff;">
                    <div style="font-size:13px; letter-spacing:0.08em; opacity:0.9;">DONOA</div>
                    <h1 style="margin:10px 0 0 0; font-size:28px; line-height:1.25;">이메일 인증 코드</h1>
                    <p style="margin:12px 0 0 0; font-size:14px; line-height:1.6; opacity:0.95;">안전한 계정 사용을 위해 아래 코드를 입력해 주세요.</p>
                  </td>
                </tr>
                <tr>
                  <td style="padding:26px 24px; border-radius:0 0 18px 18px; background:#ffffff;">
                    <p style="margin:0 0 12px 0; font-size:15px; line-height:1.7;">안녕하세요, 돈오아입니다.<br />요청하신 인증 코드를 발급해 드렸습니다.</p>
                    <div style="margin:18px 0; padding:18px; border:1px solid #d9e2ec; border-radius:14px; background:#f7fafc; text-align:center;">
                      <div style="font-size:12px; color:#486581; margin-bottom:8px;">인증 코드</div>
                      <div style="font-size:30px; letter-spacing:0.24em; font-weight:700; color:#102a43;">%s</div>
                    </div>
                    <p style="margin:0; font-size:13px; line-height:1.7; color:#627d98;">보안을 위해 본 코드는 일정 시간 후 만료됩니다. 코드 요청을 하지 않으셨다면 본 메일을 무시해 주세요.</p>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

    private final EmailSender emailSender;

    public void sendEmailConfirm(String eventId, String to, String verificationCode) {
        EmailSendResult result = emailSender.send(EmailSendCommand.builder()
                .to(to)
                .subject(EMAIL_CONFIRM_SUBJECT)
                .textBody(EMAIL_CONFIRM_TEXT_TEMPLATE.formatted(verificationCode))
                .htmlBody(EMAIL_CONFIRM_HTML_TEMPLATE.formatted(verificationCode))
                .build());

        if (result.isSuccess()) {
            log.info("EMAIL_CONFIRM_EVENT mail sent. eventId={}, provider={}", eventId, result.getProvider());
            return;
        }

        String reason = result.getErrorMessage() == null ? "unknown" : result.getErrorMessage();
        throw new IllegalStateException("EMAIL_CONFIRM_EVENT mail send failed: " + reason);
    }
}
