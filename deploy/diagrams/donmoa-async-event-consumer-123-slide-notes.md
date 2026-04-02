# DonMoa Async Event Consumer 1-2-3 Slide Notes

이 문서는 `비동기 이벤트 소비 구조` 발표에서 1, 2, 3번 슬라이드를 바로 PPT로 옮길 수 있도록 정리한 마크다운이다.
목표는 다이어그램 설명에 그치지 않고, `슬라이드 본문`, `발표 대본`, `강조 키워드`까지 한 파일에서 준비하는 것이다.

권장 순서는 아래와 같다.

1. 진입과 라우팅
2. Inbox 처리와 Worker
3. Processor 패턴

사용할 다이어그램 원본:

- [donmoa-async-event-consumer-slide-1-entry-routing.puml](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/deploy/diagrams/donmoa-async-event-consumer-slide-1-entry-routing.puml)
- [donmoa-async-event-consumer-slide-2-inbox-worker.puml](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/deploy/diagrams/donmoa-async-event-consumer-slide-2-inbox-worker.puml)
- [donmoa-async-event-consumer-slide-3-processor-patterns.puml](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/deploy/diagrams/donmoa-async-event-consumer-slide-3-processor-patterns.puml)

---

## Slide 1. 진입과 라우팅

### 이 슬라이드의 핵심 목표

청중이 먼저 이해해야 하는 것은 `Kafka listener가 실제 비즈니스 로직을 직접 처리하지 않는다`는 점이다.
비동기 소비의 첫 진입점은 단순히 메시지를 받고, 공통 라우팅 계층을 통해 `Inbox 적재` 또는 `직접 처리` 여부만 결정한다.

즉 이 슬라이드는 `consumer entry를 얇게 유지하는 구조`를 설명하는 장표다.

### 제목

`비동기 이벤트 소비 구조 1. 진입과 라우팅`

### PPT 본문

- `PaymentEventConsumer`는 Kafka 메시지를 받는 진입점만 담당합니다.
- `AbstractProcessorRoutingConsumer`가 공통 라우팅 계층 역할을 합니다.
- 여기서 `Inbox 적재` 또는 `직접 처리` 경로를 결정합니다.
- 그래서 listener에는 비즈니스 로직이 쌓이지 않습니다.

### 발표 대본

`첫 번째로 보여드릴 부분은 비동기 이벤트 소비의 진입 구조입니다.`

`여기서 핵심은 Kafka listener를 최대한 얇게 유지했다는 점입니다. 예를 들어 PaymentEventConsumer는 메시지를 받자마자 직접 주문 상태를 바꾸거나 후속 로직을 실행하지 않습니다. 대신 AbstractProcessorRoutingConsumer라는 공통 계층으로 넘겨서, 이 이벤트를 바로 처리할지 아니면 Inbox에 먼저 적재할지를 결정합니다.`

`즉 listener의 책임은 Kafka 진입점에 한정하고, 라우팅과 실제 처리 책임은 뒤쪽 공통 계층으로 분리한 구조입니다. 이렇게 해두면 각 서비스가 consumer를 새로 만들더라도 진입 구조는 공통화되고, 비즈니스 로직은 processor 쪽에만 집중시킬 수 있습니다.`

### 발표할 때 강조할 단어

- `thin listener`
- `공통 라우팅`
- `Inbox 또는 direct`
- `비즈니스 로직 분리`

### 슬라이드 하단 한 줄

`Consumer 진입점은 메시지를 받기만 하고, 공통 라우팅 계층이 처리 경로를 결정합니다.`

### 같이 보면 좋은 코드 포인트

- [PaymentEventConsumer.java](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/servers/services/order/src/main/java/com/example/order/consumer/payment/PaymentEventConsumer.java)
- [AbstractProcessorRoutingConsumer.java](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/libs/event/inbox/src/main/java/com/example/event/inbox/AbstractProcessorRoutingConsumer.java)

---

## Slide 2. Inbox 처리와 Worker

### 이 슬라이드의 핵심 목표

두 번째 슬라이드에서는 왜 Inbox 계층을 두었는지 설명해야 한다.
청중이 이해해야 하는 포인트는 `메시지를 받자마자 실행하지 않고, 먼저 저장한 뒤 worker가 재시도와 복구를 포함해 처리한다`는 점이다.

즉 이 슬라이드는 `안정성을 공통 계층으로 끌어올린 구조`를 설명하는 장표다.

### 제목

`비동기 이벤트 소비 구조 2. Inbox 처리와 Worker`

### PPT 본문

- `InboxEnqueueService`가 메시지를 먼저 안전하게 저장합니다.
- `InboxWorkerScheduler`가 적재된 메시지를 가져와 처리합니다.
- 실패 시 재시도와 stale recovery를 worker가 공통 처리합니다.
- 서비스는 각자 retry 로직을 중복 구현하지 않아도 됩니다.

### 발표 대본

`두 번째는 안정성을 담당하는 Inbox 계층입니다. 저희는 consumer가 메시지를 받자마자 바로 비즈니스 로직을 실행하지 않고, 먼저 Inbox에 저장하는 구조를 사용했습니다.`

`이렇게 하면 처리 도중 장애가 나더라도 메시지를 유실하지 않고 다시 가져올 수 있습니다. 그다음 InboxWorkerScheduler가 적재된 메시지를 조회해서 처리하고, 실패하면 재시도하거나, 처리 중 멈춰서 lease가 만료된 메시지는 stale recovery로 다시 복구합니다.`

`중요한 점은 이런 중복 방지, 재처리, 복구 책임을 각 서비스가 따로 구현하지 않는다는 것입니다. 공통 Inbox 계층이 이 문제를 맡기 때문에, 개별 서비스는 자기 processor와 도메인 로직에 집중할 수 있습니다.`

### 발표할 때 강조할 단어

- `안전하게 저장`
- `재시도`
- `stale recovery`
- `공통 안정성 계층`

### 슬라이드 하단 한 줄

`Inbox는 메시지를 먼저 보관하고, Worker가 재시도와 복구를 포함해 안정적으로 처리합니다.`

### 같이 보면 좋은 코드 포인트

- [InboxWorkerScheduler.java](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/libs/event/inbox/src/main/java/com/example/event/inbox/InboxWorkerScheduler.java)
- [InboxEnqueueService.java](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/libs/event/inbox/src/main/java/com/example/event/inbox/InboxEnqueueService.java)

---

## Slide 3. Processor 패턴

### 이 슬라이드의 핵심 목표

세 번째 슬라이드에서는 `처리 로직도 서비스 특성에 맞게 공통 패턴 위에서 확장할 수 있다`는 점을 보여줘야 한다.
핵심은 processor를 한 가지 구현 방식으로 강제하지 않고, 이벤트 형태에 맞게 `EventSpec`과 `RouteSpec` 두 계열을 제공했다는 것이다.

즉 이 슬라이드는 `공통화와 서비스별 유연성의 균형`을 설명하는 장표다.

### 제목

`비동기 이벤트 소비 구조 3. Processor 패턴`

### PPT 본문

- Processor가 실제 비즈니스 처리 직전의 핵심 계층입니다.
- `EventSpec` 계열은 이벤트 타입별 payload와 action이 다를 때 적합합니다.
- `RouteSpec` 계열은 하나의 payload 모델 안에서 여러 타입을 분기할 때 적합합니다.
- 두 방식 모두 멱등성과 이벤트 분기를 processor 내부에서 통제합니다.

### 발표 대본

`세 번째는 실제 처리 계층인 processor 패턴입니다. 여기서 포인트는 processor를 한 가지 방식으로 고정하지 않았다는 점입니다. 서비스마다 이벤트 모델이 다르기 때문에, 저희는 EventSpec 계열과 RouteSpec 계열 두 가지 패턴을 제공합니다.`

`예를 들어 OrderPaymentEventProcessor는 PAYMENT_COMPLETED, PAYMENT_FAILED처럼 이벤트 타입별로 서로 다른 payload 검증과 후속 액션이 필요하기 때문에 EventSpec 계열이 잘 맞습니다. 반대로 ChatFundingEventProcessor는 FundingEventMessage 하나를 공통 payload로 쓰면서 FUNDING_CREATED, FUNDING_PARTICIPATED 같은 타입만 라우팅하면 되기 때문에 RouteSpec 계열이 더 적합합니다.`

`하지만 두 방식의 공통점은 같습니다. 실제 비즈니스 처리는 processor가 맡고, 그 안에서 멱등 처리와 이벤트 분기를 통제한다는 점입니다. 그래서 consumer entry는 단순하게 유지하면서도, 서비스별 도메인 처리 방식은 유연하게 가져갈 수 있습니다.`

### 발표할 때 강조할 단어

- `processor`
- `idempotent`
- `EventSpec`
- `RouteSpec`

### 슬라이드 하단 한 줄

`Processor는 멱등성과 이벤트 분기를 공통화하면서도, 서비스별 처리 방식은 유연하게 확장할 수 있게 합니다.`

### 같이 보면 좋은 코드 포인트

- [OrderPaymentEventProcessor.java](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/servers/services/order/src/main/java/com/example/order/consumer/payment/OrderPaymentEventProcessor.java)
- [ChatFundingEventProcessor.java](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/servers/services/chat/src/main/java/com/example/chat/consumer/ChatFundingEventProcessor.java)
- [AbstractIdempotentEventSpecProcessor.java](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/libs/event/consumer/src/main/java/com/example/event/consumer/AbstractIdempotentEventSpecProcessor.java)
- [AbstractIdempotentRoutingJsonMessageConsumer.java](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/libs/event/consumer/src/main/java/com/example/event/consumer/AbstractIdempotentRoutingJsonMessageConsumer.java)

---

## 세 장 연결 마무리 멘트

`정리하면 저희 비동기 이벤트 소비 구조는 세 단계로 이해하시면 됩니다. 첫째, consumer entry는 Kafka 진입점 역할만 하고 공통 라우팅으로 넘깁니다. 둘째, Inbox와 Worker가 메시지를 안전하게 저장하고 재시도와 복구를 맡습니다. 셋째, processor가 멱등성과 이벤트 분기를 포함한 실제 비즈니스 처리를 담당합니다.`

`결국 이 구조의 목적은 비동기 소비를 서비스마다 제각각 구현하지 않고, 공통 안정성은 인프라 계층으로 올리고, 서비스 코드는 도메인 처리에 집중하게 만드는 것입니다.`
