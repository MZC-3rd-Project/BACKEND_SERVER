# Module: `libs/security/crypto`

## 이 모듈이 해결하는 문제
민감정보 암복호화/로그 마스킹을 서비스마다 따로 구현하면 보안 규칙이 쉽게 어긋납니다.
`security/crypto`는 공통 암복호화 유틸과 마스킹 유틸을 제공합니다.

## 언제 사용하면 되나요?
- 개인정보/민감정보를 저장 전에 암호화해야 할 때
- 로그에 민감 데이터 노출을 줄이고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:security:crypto"))
```
