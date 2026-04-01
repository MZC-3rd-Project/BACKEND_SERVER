# DonMoa Sync Facade Communication

이 문서는 `draw.io` 대신 Mermaid로 정리한 facade 기반 동기 통신 구조 설명 자료다.
발표나 문서 공유 시 그대로 렌더링해서 사용할 수 있다.

## 1. Class Diagram

```mermaid
classDiagram
direction LR

class HotDealCheckoutService {
  -productClient: ProductItemQueryClientFacade
  -orderCreateClientFacade: OrderCreateClientFacade
  +reserve(hotDealId, request, userId)
  +submit(request, userId)
}

class ProductItemQueryClientFacade {
  <<interface>>
  +findItem(itemId)
}

class ProductItemSummaryClientFacade {
  <<interface>>
  +findItemSummary(itemId)
}

class ProductQuoteClientFacade {
  <<interface>>
  +quoteItems(request)
}

class ProductEndingSoonClientFacade {
  <<interface>>
  +findItemsEndingSoon()
}

class ProductClientFacade {
  <<interface>>
}

class DefaultProductClientFacade {
  -webClient: WebClient
  -objectMapper: ObjectMapper
  +findItemSummary(itemId)
  +findItem(itemId)
  +quoteItems(request)
  +findItemsEndingSoon()
}

class OrderCreateClientFacade {
  <<interface>>
  +createOrder(request)
}

class DefaultOrderClientFacade {
  -webClient: WebClient
  -objectMapper: ObjectMapper
  +createOrder(request)
  -validate(request)
  -mapResponse(result)
}

class ProductClientAutoConfiguration {
  +defaultProductClientFacade(...)
  +productClientFacade(delegate)
  +productItemQueryClientFacade(delegate)
  +productItemSummaryClientFacade(delegate)
  +productQuoteClientFacade(delegate)
  +productEndingSoonClientFacade(delegate)
}

class OrderClientAutoConfiguration {
  +defaultOrderClientFacade(...)
  +orderCreateClientFacade(delegate)
}

HotDealCheckoutService --> ProductItemQueryClientFacade : uses
HotDealCheckoutService --> OrderCreateClientFacade : uses

ProductItemQueryClientFacade <|-- ProductClientFacade
ProductItemSummaryClientFacade <|-- ProductClientFacade
ProductQuoteClientFacade <|-- ProductClientFacade
ProductEndingSoonClientFacade <|-- ProductClientFacade

ProductClientFacade <|.. DefaultProductClientFacade
OrderCreateClientFacade <|.. DefaultOrderClientFacade

ProductClientAutoConfiguration --> DefaultProductClientFacade : creates
ProductClientAutoConfiguration --> ProductClientFacade : exposes as bean
ProductClientAutoConfiguration --> ProductItemQueryClientFacade : exposes as bean
ProductClientAutoConfiguration --> ProductItemSummaryClientFacade : exposes as bean
ProductClientAutoConfiguration --> ProductQuoteClientFacade : exposes as bean
ProductClientAutoConfiguration --> ProductEndingSoonClientFacade : exposes as bean

OrderClientAutoConfiguration --> DefaultOrderClientFacade : creates
OrderClientAutoConfiguration --> OrderCreateClientFacade : exposes as bean
```

## 2. Usage Example: reserve()

```mermaid
sequenceDiagram
autonumber
participant H as HotDealCheckoutService
participant P as ProductItemQueryClientFacade
participant DP as DefaultProductClientFacade
participant PS as Product Service

H->>P: findItem(itemId)
P->>DP: delegate
DP->>PS: GET /internal/v1/items/{itemId}
PS-->>DP: { success, data }
DP-->>H: JsonNode item
H->>H: sellerId / storeId / itemType 추출
H->>H: HotDealCheckoutSession 생성
```

## 3. Usage Example: submit()

```mermaid
sequenceDiagram
autonumber
participant H as HotDealCheckoutService
participant O as OrderCreateClientFacade
participant DO as DefaultOrderClientFacade
participant OS as Order Service

H->>O: createOrder(request)
O->>DO: delegate
DO->>DO: validate(request)
DO->>OS: POST /internal/v1/orders
OS-->>DO: success / 409 / 5xx

alt success
  DO-->>H: OrderCreateResponse
  H->>H: session 상태를 ORDER_CREATED로 저장
else 409 Conflict
  DO-->>H: OrderClientConflictException
  H->>H: replay 로 간주하고 계속 진행
else other client/server error
  DO-->>H: OrderClientException
  H->>H: BusinessException(ORDER_SERVICE_ERROR) 변환
end
```

## 4. Presentation Point

- 서비스는 downstream HTTP 세부사항을 직접 다루지 않고, 비즈니스 의미가 드러나는 facade interface만 사용한다.
- 실제 동기 통신 구현, 응답 파싱, 예외 매핑은 `Default*Facade` 안으로 숨겨진다.
- `AutoConfiguration`이 같은 구현체를 여러 facade bean으로 노출해서, 사용처는 필요한 능력만 주입받을 수 있다.
- 이 패턴 덕분에 application service 코드는 orchestration에 집중하고, 동기 통신 코드는 공통 라이브러리로 재사용된다.
