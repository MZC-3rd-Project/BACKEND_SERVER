# 아이템 등록 플로우

장표 파일: [donmoa-item-create-flow.drawio](./donmoa-item-create-flow.drawio)

## 장표 핵심 메시지

- 클라이언트는 `POST /bff/v1/products` 한 번만 호출한다.
- Gateway BFF가 상품 생성, 이미지 등록, 상세 재조회를 하나의 흐름으로 묶는다.
- Product 서비스는 상품 저장과 이벤트 발행을 담당하고, 이미지 단계는 별도 command service가 처리한다.
- 등록 이후 Search 서비스가 `item-events`를 소비해 Elasticsearch 인덱스를 비동기로 갱신한다.

## 발표 대사 1분 버전

이 장표는 아이템 등록이 실제로 어떤 순서로 처리되는지를 보여줍니다.
클라이언트는 `POST /bff/v1/products` 한 번만 호출하지만,
Gateway BFF 내부에서는 먼저 Product Service로 상품을 생성하고,
이미지가 있으면 `/api/items/{itemId}/images`를 호출해서 이미지까지 연결한 뒤,
마지막에 상세를 다시 조회해서 최종 응답을 반환합니다.

그리고 여기서 끝나는 게 아니라,
등록 과정에서 발행된 `ItemCreatedEvent`나 `ItemUpdatedEvent`를 Search 서비스가 받아서
Elasticsearch 인덱스도 비동기로 갱신합니다.

즉, 클라이언트는 한 번만 호출하지만,
Gateway가 상품 생성, 이미지 등록, 상세 재조회를 하나의 등록 흐름으로 묶어 주고,
검색 반영은 이벤트 기반으로 뒤따라오는 구조입니다.

## 발표 대사 상세 버전

이 장표는 아이템 등록 시 실제로 어떤 순서로 요청이 흐르는지를 보여줍니다.
왼쪽 메인 플로우를 위에서 아래로 보시면 됩니다.

먼저 1단계에서 판매자나 관리자 클라이언트가
`POST /bff/v1/products` 요청을 보냅니다.
클라이언트 입장에서는 이 한 번의 호출만 보게 됩니다.

2단계에서는 Gateway BFF가 이 요청을 받습니다.
여기서는 `BusinessProductMediaBffService` 또는 같은 패턴의 `ProductMediaBffService`가 동작하고,
인증이 필요한 쓰기 요청이기 때문에 `withAuthHeaders(true)`로 downstream 인증 헤더를 붙입니다.

3단계에서는 Product Service의 `POST /api/products`를 호출합니다.
컨트롤러에서는 `ProductCommandController`가 받고,
실제 비즈니스 로직은 `ProductCommandService`가 처리합니다.
오른쪽 상단 카드처럼 여기서 판매자의 store ownership을 검증하고,
thumbnail media 참조와 category를 확인한 뒤 Item을 저장하고,
마지막에는 `ItemCreatedEvent`까지 발행합니다.

그 다음 4단계는 이미지가 있는 경우에만 수행되는 선택 단계입니다.
Gateway가 `/api/items/{itemId}/images`를 호출하면
`ItemImageCommandController`와 `ItemImageCommandService`가 처리합니다.
오른쪽 가운데 카드처럼 여기서는 media 참조를 다시 검증하고,
ItemImage를 저장하고,
썸네일과 sort order를 정규화한 뒤,
`MediaReferenceService`를 통해 링크를 동기화하고 `ItemUpdatedEvent`를 발행합니다.

마지막 5단계에서는 Gateway가 다시 상세 조회를 수행합니다.
즉, 생성 직후 상태를 그대로 돌려주는 것이 아니라,
이미지 처리까지 반영된 최종 상세를 재조회해서 클라이언트에 반환합니다.

그리고 오른쪽 아래의 마지막 카드가 Elasticsearch까지 포함한 비동기 후속 흐름입니다.
3단계의 `ProductCommandService`에서는 `ItemCreatedEvent`가 발행되고,
4단계의 이미지 처리에서는 `ItemUpdatedEvent`가 발행됩니다.
이 이벤트들은 `item-events` 토픽으로 나가고,
Search 서비스의 `ItemEventConsumer`와 `SearchItemEventProcessor`가 이를 소비합니다.
그 다음 `ElasticsearchIndexingService`가 상품, 재고, 스토어, 펀딩 정보를 다시 모아서
최종 검색 문서를 만들고,
`ElasticsearchDocumentClient`가 Elasticsearch에 upsert 합니다.

즉, 사용자는 상품 등록 응답을 먼저 받고,
검색 반영은 이벤트 기반으로 뒤따라오는 eventual consistency 구조라고 이해하시면 됩니다.

그래서 이 구조의 핵심은
클라이언트는 단순히 "등록 요청" 한 번만 보내고,
그 뒤의 상품 생성, 이미지 연결, 상세 재조회 같은 복잡한 순차 흐름은
Gateway와 downstream 서비스가 내부적으로 나눠서 처리한다는 점입니다.
그리고 검색 색인까지 포함하면,
동기 등록 흐름과 비동기 검색 반영 흐름이 결합된 구조라고 볼 수 있습니다.

## 발표자 메모

- 왼쪽은 메인 HTTP 흐름, 오른쪽은 각 단계 내부 처리 포인트라고 먼저 설명하면 이해가 쉽다.
- 4단계는 "이미지가 있을 때만 수행되는 선택 단계"라고 꼭 짚는 게 좋다.
- Elasticsearch 부분은 "동기 등록 응답"과 "비동기 검색 반영"을 구분해서 설명하는 편이 좋다.
- 마지막에는 "클라이언트는 한 번만 호출하지만 내부는 동기 등록 + 비동기 색인으로 나뉜다"는 문장으로 정리하면 된다.
