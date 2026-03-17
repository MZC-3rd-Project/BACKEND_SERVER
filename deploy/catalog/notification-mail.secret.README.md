# Notification Mail Secret

Create this secret in AWS Secrets Manager before deploying `notification-service` with Gmail SMTP:

- Secret name: `donmoa/dev/notification-mail`

Expected JSON payload:

```json
{
  "MAIL_USERNAME": "your-account@example.com",
  "MAIL_PASSWORD": "google-app-password",
  "NOTIFICATION_EMAIL_FROM": "your-account@example.com"
}
```

Notes:

- Use a Google App Password, not the regular account password.
- `MAIL_HOST` and `MAIL_PORT` are already fixed in `deploy/helm/environments/dev/notification.yaml` to `smtp.gmail.com:587`.
- Rotate the App Password after moving it into Secrets Manager if it was shared in chat or any temporary channel.
