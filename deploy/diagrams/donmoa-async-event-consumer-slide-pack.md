# 비동기 이벤트 소비 다이어그램 모음

PPT에 바로 옮기기 쉽게 박스형 다이어그램과 설명을 한곳에 모아둔 문서다.

## 1. 진입과 라우팅

파일: [donmoa-async-event-consumer-slide-1-entry-routing.puml](./donmoa-async-event-consumer-slide-1-entry-routing.puml)

설명:
- `PaymentEventConsumer`는 Kafka에서 메시지를 받는 실제 진입 클래스다.
- 이 클래스는 메시지를 직접 처리하지 않고 공통 라우팅 구조로 넘긴다.
- `InboxRoutingSupport`가 Inbox 적재 여부를 판단하고, 직접 처리라면 `EventMessageProcessor`로 위임한다.

발표 포인트:
- "핵심은 Listener를 얇게 유지하는 것입니다."
- "메시지를 받자마자 비즈니스 로직으로 들어가지 않고, 먼저 공통 라우팅 계층을 거칩니다."

```plantuml
@startuml
title 비동기 이벤트 소비 구조 - 1. 진입과 라우팅

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam linetype ortho
skinparam packageStyle rectangle
skinparam defaultTextAlignment center

skinparam class {
  BackgroundColor #FFFFFF
  BorderColor #CBD5E1
  ArrowColor #475569
  FontColor #0F172A
  HeaderBackgroundColor #F8FAFC
}

skinparam interface {
  BackgroundColor #F8FAFC
  BorderColor #94A3B8
  FontColor #0F172A
}

skinparam note {
  BackgroundColor #EFF6FF
  BorderColor #93C5FD
  FontColor #1E3A8A
}

left to right direction

class "Kafka Topic\npayment-events" as KafkaTopic <<external>>

class "PaymentEventConsumer" as PaymentEventConsumer {
  + consume(record): void
}

abstract class "AbstractProcessorRoutingConsumer" as AbstractProcessorRoutingConsumer

class "RoutedEventConsumer" as RoutedEventConsumer <<annotation>> {
  + consumerName(): String
  + defaultMode(): ConsumerRoutingMode
}

enum "ConsumerRoutingMode" as ConsumerRoutingMode {
  DIRECT
  INBOX
}

interface "EventMessageProcessor" as EventMessageProcessor {
  + supports(eventType): boolean
  + process(message, eventId, eventType): void
}

class "InboxRoutingSupport" as InboxRoutingSupport {
  + isInbox(consumerType): boolean
  + enqueue(consumerType, envelope, message): boolean
}

class "EventConsumerRoutingResolver" as EventConsumerRoutingResolver {
  + resolveMode(consumerType): ConsumerRoutingMode
}

class "EventConsumerRoutingProperties" as EventConsumerRoutingProperties

KafkaTopic --> PaymentEventConsumer : 이벤트 레코드 전달
PaymentEventConsumer --|> AbstractProcessorRoutingConsumer
PaymentEventConsumer ..> RoutedEventConsumer
AbstractProcessorRoutingConsumer --> InboxRoutingSupport : 처리 경로 결정
AbstractProcessorRoutingConsumer --> EventMessageProcessor : 직접 처리 위임
InboxRoutingSupport --> EventConsumerRoutingResolver : 라우팅 설정 조회
EventConsumerRoutingResolver --> EventConsumerRoutingProperties

note bottom of AbstractProcessorRoutingConsumer
Listener 구현체는 얇게 유지한다.
Kafka 레코드를 받은 뒤
Inbox 적재 또는 직접 처리만 결정한다.
end note

@enduml
```

## 2. Inbox 처리와 Worker

파일: [donmoa-async-event-consumer-slide-2-inbox-worker.puml](./donmoa-async-event-consumer-slide-2-inbox-worker.puml)

설명:
- `InboxEnqueueService`는 메시지를 바로 실행하지 않고 먼저 저장한다.
- `InboxWorkerScheduler`는 저장된 메시지를 가져와 재시도와 복구를 포함해 처리한다.
- `InboxHandlerRegistry`를 통해 어떤 Processor가 이 메시지를 맡아야 하는지 찾아 연결한다.

발표 포인트:
- "Inbox를 두는 이유는 안정성 때문입니다."
- "중복 메시지, 일시 장애, 재처리를 공통 계층에서 해결합니다."

```plantuml
@startuml
title 비동기 이벤트 소비 구조 - 2. Inbox 처리와 Worker

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam linetype ortho
skinparam packageStyle rectangle
skinparam defaultTextAlignment center

skinparam class {
  BackgroundColor #FFFFFF
  BorderColor #CBD5E1
  ArrowColor #475569
  FontColor #0F172A
  HeaderBackgroundColor #F8FAFC
}

skinparam interface {
  BackgroundColor #F8FAFC
  BorderColor #94A3B8
  FontColor #0F172A
}

skinparam note {
  BackgroundColor #F0FDF4
  BorderColor #86EFAC
  FontColor #166534
}

top to bottom direction

class "InboxRoutingSupport" as InboxRoutingSupport

class "InboxEnqueueService" as InboxEnqueueService {
  + enqueue(consumerName, eventId, eventType, payload): boolean
}

interface "InboxRepository" as InboxRepository
class "InboxMessage" as InboxMessage

interface "InboxSignalPublisher" as InboxSignalPublisher {
  + signal(consumerName): void
}

class "InboxWorkerScheduler" as InboxWorkerScheduler {
  + signal(consumerName): void
  + pollAndProcess(): void
}

class "InboxHandlerRegistry" as InboxHandlerRegistry {
  + findHandler(consumerName, eventType): Optional<InboxEventHandler>
}

interface "InboxEventHandler" as InboxEventHandler {
  + handle(eventId, eventType, payload): void
}

class "ProcessorBackedInboxEventHandler" as ProcessorBackedInboxEventHandler

interface "EventMessageProcessor" as EventMessageProcessor {
  + process(message, eventId, eventType): void
}

InboxRoutingSupport --> InboxEnqueueService : Inbox 적재 요청
InboxEnqueueService --> InboxRepository : pending 메시지 저장
InboxEnqueueService --> InboxSignalPublisher : 커밋 후 처리 신호
InboxWorkerScheduler ..|> InboxSignalPublisher
InboxWorkerScheduler --> InboxRepository : 조회 / 점유 / 재시도 / 상태 반영
InboxWorkerScheduler --> InboxHandlerRegistry : 처리 핸들러 조회
InboxHandlerRegistry --> InboxEventHandler
ProcessorBackedInboxEventHandler ..|> InboxEventHandler
ProcessorBackedInboxEventHandler --> EventMessageProcessor
InboxRepository --> InboxMessage

note right of InboxWorkerScheduler
Worker는 Inbox에 쌓인 메시지를 가져와
재시도와 stale recovery를 처리하고
맞는 Processor로 위임한다.
end note

@enduml
```

## 3. Processor 패턴

파일: [donmoa-async-event-consumer-slide-3-processor-patterns.puml](./donmoa-async-event-consumer-slide-3-processor-patterns.puml)

설명:
- Processor는 실제 비즈니스 처리 직전의 핵심 계층이다.
- `EventSpec` 계열은 이벤트 타입마다 다른 payload와 액션이 필요한 경우에 적합하다.
- `RouteSpec` 계열은 하나의 payload 모델 안에서 여러 이벤트 타입을 분기할 때 적합하다.

발표 포인트:
- "서비스마다 Processor 구현 방식은 조금 다르지만 목적은 같습니다."
- "멱등 처리와 이벤트 분기를 Processor에서 통제합니다."

```plantuml
@startuml
title 비동기 이벤트 소비 구조 - 3. Processor 패턴

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam linetype ortho
skinparam packageStyle rectangle
skinparam defaultTextAlignment center

skinparam class {
  BackgroundColor #FFFFFF
  BorderColor #CBD5E1
  ArrowColor #475569
  FontColor #0F172A
  HeaderBackgroundColor #F8FAFC
}

skinparam interface {
  BackgroundColor #F8FAFC
  BorderColor #94A3B8
  FontColor #0F172A
}

skinparam note {
  BackgroundColor #FFF7ED
  BorderColor #FDBA74
  FontColor #7C2D12
}

left to right direction

interface "EventMessageProcessor" as EventMessageProcessor
interface "EventEnvelope" as EventEnvelope {
  + getEventId(): String
  + getEventType(): String
}

class "IdempotentConsumerService" as IdempotentConsumerService
class "InboxConsumerBinding" as InboxConsumerBinding <<annotation>>

package "EventSpec 계열" #FFF7ED {
  abstract class "AbstractIdempotentEventSpecProcessor" as AbstractIdempotentEventSpecProcessor {
    # eventSpecs(): Map<String, EventSpec>
    # idempotentEventType(): String
  }

  class "EventSpec<T>" as EventSpec

  class "OrderPaymentEventProcessor" as OrderPaymentEventProcessor
  class "PaymentEventMessage" as PaymentEventMessage
}

package "RouteSpec 계열" #FEF3C7 {
  abstract class "AbstractIdempotentRoutingJsonMessageConsumer<T>" as AbstractIdempotentRoutingJsonMessageConsumer {
    # routeSpecs(): Map<String, RouteSpec<T>>
    # payloadType(): Class<T>
    # idempotentEventType(): String
  }

  class "RouteSpec<T>" as RouteSpec

  class "ChatFundingEventProcessor" as ChatFundingEventProcessor
  class "FundingEventMessage" as FundingEventMessage
}

AbstractIdempotentEventSpecProcessor ..|> EventMessageProcessor
AbstractIdempotentEventSpecProcessor --> IdempotentConsumerService
AbstractIdempotentEventSpecProcessor --> EventSpec
OrderPaymentEventProcessor --|> AbstractIdempotentEventSpecProcessor
OrderPaymentEventProcessor ..> InboxConsumerBinding
PaymentEventMessage ..|> EventEnvelope
OrderPaymentEventProcessor --> PaymentEventMessage

AbstractIdempotentRoutingJsonMessageConsumer ..|> EventMessageProcessor
AbstractIdempotentRoutingJsonMessageConsumer --> IdempotentConsumerService
AbstractIdempotentRoutingJsonMessageConsumer --> RouteSpec
ChatFundingEventProcessor --|> AbstractIdempotentRoutingJsonMessageConsumer
ChatFundingEventProcessor ..> InboxConsumerBinding
FundingEventMessage ..|> EventEnvelope
ChatFundingEventProcessor --> FundingEventMessage

note bottom of EventSpec
eventType마다
서로 다른 payload 클래스와 action이
필요할 때 사용한다.
end note

note bottom of RouteSpec
하나의 payload 모델 안에서
여러 eventType을 분기 처리할 때
사용한다.
end note

@enduml
```

## 4. 서비스 적용 예시

파일: [donmoa-async-event-consumer-slide-4-service-examples.puml](./donmoa-async-event-consumer-slide-4-service-examples.puml)

설명:
- `Order 서비스`는 결제 이벤트를 받아 주문 상태를 바꾸고 후속 이벤트를 발행한다.
- `Chat 서비스`는 펀딩 이벤트를 받아 채팅방과 참여자 상태를 동기화한다.
- 공통 구조는 같고, Processor 뒤쪽의 비즈니스 역할만 달라진다.

발표 포인트:
- "구조를 공통화했기 때문에 서비스마다 Listener를 새로 설계할 필요가 없습니다."
- "각 서비스는 자기 Processor와 도메인 로직에만 집중할 수 있습니다."

```plantuml
@startuml
title 비동기 이벤트 소비 구조 - 4. 서비스 적용 예시

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam linetype ortho
skinparam packageStyle rectangle
skinparam defaultTextAlignment center

skinparam class {
  BackgroundColor #FFFFFF
  BorderColor #CBD5E1
  ArrowColor #475569
  FontColor #0F172A
  HeaderBackgroundColor #F8FAFC
}

skinparam interface {
  BackgroundColor #F8FAFC
  BorderColor #94A3B8
  FontColor #0F172A
}

skinparam note {
  BackgroundColor #EFF6FF
  BorderColor #93C5FD
  FontColor #1E3A8A
}

left to right direction

package "Order 서비스" #EFF6FF {
  class "PaymentEventConsumer" as PaymentEventConsumer {
    + consume(record): void
  }

  class "OrderPaymentEventProcessor" as OrderPaymentEventProcessor
  interface "OrderRepository" as OrderRepository
  interface "EventPublisher" as EventPublisher

  PaymentEventConsumer --> OrderPaymentEventProcessor
  OrderPaymentEventProcessor --> OrderRepository
  OrderPaymentEventProcessor --> EventPublisher
}

package "Chat 서비스" #F0FDF4 {
  class "FundingEventConsumer" as FundingEventConsumer {
    + consume(record): void
  }

  class "ChatFundingEventProcessor" as ChatFundingEventProcessor
  class "ChatFundingSyncService" as ChatFundingSyncService

  FundingEventConsumer --> ChatFundingEventProcessor
  ChatFundingEventProcessor --> ChatFundingSyncService
}

note bottom
두 서비스 모두 Listener는 얇게 유지한다.
Processor가 비즈니스 흐름을 소유하고
Service 또는 Repository와 협력한다.
end note

@enduml
```
