# Module: `libs/config/kafka-core`

## 이 모듈이 해결하는 문제
Kafka 관련 기본 속성을 서비스마다 각자 정의하면 운영 기준이 쉽게 분산됩니다.
`kafka-core`는 Kafka 공통 설정 키와 기본 의존성 베이스를 제공합니다.

## 언제 사용하면 되나요?
- Kafka consumer 공통 속성(`app.kafka.consumer.*`)을 사용하고 싶을 때
- Kafka 모듈을 역할별로 분리해 조합하고 싶을 때

## 핵심 제공
- `KafkaConsumerProperties`

## 빠른 시작
```text
implementation(project(":libs:config:kafka-core"))
```
