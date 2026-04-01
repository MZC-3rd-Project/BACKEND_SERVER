# DB Read/Write 분리와 `@UseWriteDataSource` 발표 자료 V2

기존 장표보다 발표 메시지가 먼저 보이도록 다시 정리한 버전이다.
실제 클래스 이름은 유지하되, 슬라이드마다 핵심 메시지 하나만 보이게 구성했다.

## 사용할 장표

- [donmoa-read-write-datasource-slide-v2-1-overview.puml](./donmoa-read-write-datasource-slide-v2-1-overview.puml)
- [donmoa-read-write-datasource-slide-v2-2-rule.puml](./donmoa-read-write-datasource-slide-v2-2-rule.puml)
- [donmoa-read-write-datasource-slide-v2-3-example.puml](./donmoa-read-write-datasource-slide-v2-3-example.puml)

## 장표별 핵심 메시지

- 1번 슬라이드: 애플리케이션은 `routingDataSource` 하나만 본다.
- 2번 슬라이드: 기본은 `readOnly -> READ`지만, `@UseWriteDataSource`가 예외적으로 WRITE를 강제한다.
- 3번 슬라이드: 서비스 코드는 annotation으로 의도만 표현하고, 실제 라우팅은 공통 인프라가 처리한다.

## 슬라이드 진행 순서에 맞춘 발표 대사 상세 버전

### 1번 슬라이드 보여주고

첫 번째 슬라이드는 전체 구조를 한눈에 보여주는 장표입니다.
여기서 가장 먼저 보실 부분은 가운데의 `routingDataSource`입니다.
이 클래스의 실제 구현이 `TransactionRoutingDataSource`이고,
애플리케이션과 JPA는 이 대상을 통해서만 DB에 접근합니다.

즉, 서비스나 Repository 입장에서는 datasource를 하나처럼 사용합니다.
하지만 내부적으로는 아래쪽의 `writeDataSource`와 `readDataSource`로 갈라질 수 있습니다.
이 분리를 만들어 주는 시작점이 위쪽의 `ReadWriteDataSourceAutoConfiguration`입니다.
이 설정이 켜지면 write, read, routing datasource가 한 번에 등록됩니다.

그래서 이 슬라이드의 핵심은
"개발자는 datasource를 여러 개 직접 고르지 않고,
공통 모듈이 제공하는 routingDataSource 하나만 사용한다"입니다.

그리고 오른쪽 아래 노트처럼
read 설정이 아직 없으면 write로 폴백할 수 있어서,
이 구조를 한 번에 전부 적용하지 않아도 단계적으로 도입할 수 있습니다.

정리하면 1번 슬라이드는
read/write 분리가 서비스 코드에 흩어져 있는 게 아니라,
공통 인프라 안에 모여 있다는 점을 설명하는 장표입니다.

### 2번 슬라이드 보여주고

두 번째 슬라이드는 실제로 READ와 WRITE가 어떻게 결정되는지를 설명합니다.
여기서는 가운데의 `TransactionRoutingDataSource`를 중심으로 보면 됩니다.

이 클래스는 먼저 왼쪽의 `DataSourceRoutingContext`를 확인합니다.
즉, 이번 호출에 대해 강제로 지정된 경로가 있는지부터 먼저 봅니다.
만약 여기에서 WRITE가 들어 있으면 그 값을 우선 사용합니다.

강제 경로가 없을 때만 아래쪽의 `@Transactional(readOnly = true)` 같은 기본 규칙이 적용됩니다.
그래서 기본 정책은 단순합니다.
readOnly 트랜잭션이면 READ,
그 외에는 WRITE입니다.

그런데 여기서 예외를 만드는 장치가 왼쪽의 `UseWriteDataSourceAspect`입니다.
`@UseWriteDataSource`가 붙은 메서드나 클래스가 실행되면,
이 Aspect가 `DataSourceRoutingContext`에 WRITE를 넣어 줍니다.
그러면 `TransactionRoutingDataSource`는 readOnly 여부를 보기 전에
이미 강제된 WRITE 경로를 발견하게 됩니다.

즉, 이 슬라이드의 핵심은
"기본 규칙은 readOnly면 READ지만,
`@UseWriteDataSource`가 붙으면 그 기본 규칙보다 WRITE 강제가 우선한다"입니다.

발표할 때는
`UseWriteDataSourceAspect -> DataSourceRoutingContext -> TransactionRoutingDataSource -> READ/WRITE`
이 순서로 따라가면서 설명하면 이해가 빠릅니다.

### 3번 슬라이드 보여주고

세 번째 슬라이드는 이 규칙이 실제 서비스 코드에서 어떻게 쓰이는지를 보여주는 예시입니다.
여기서는 위쪽 두 영역을 비교해서 보시면 됩니다.

먼저 왼쪽의 `ParticipationQueryService`는 기본적으로 readOnly 조회 서비스입니다.
그런데 그 안의 `findByUserId`, `findByCampaignId` 같은 특정 메서드에는
`@UseWriteDataSource`를 붙여서 WRITE를 강제하고 있습니다.
즉, 전체 서비스는 조회 서비스지만,
일부 조회는 최신성이 더 중요해서 writer DB를 보겠다는 의도를 메서드 단위로 표현한 것입니다.

반대로 오른쪽의 `InternalCampaignQueryService`는
클래스 자체에 `@UseWriteDataSource`를 붙인 예시입니다.
이 경우에는 서비스의 모든 조회가 writer DB를 사용하게 됩니다.
즉, 이 annotation은 메서드 단위와 클래스 단위 둘 다 적용할 수 있습니다.

아래쪽을 보시면
이 annotation 정보는 결국 `UseWriteDataSourceAspect`가 해석하고,
최종적으로는 `TransactionRoutingDataSource`가 WRITE를 선택하게 됩니다.
여기서 중요한 점은 서비스 코드가 datasource를 직접 바꾸지 않는다는 것입니다.
서비스는 그냥 annotation으로 "이 조회는 WRITE가 필요하다"는 의도만 표현하고,
실제 라우팅은 공통 인프라가 맡습니다.

그래서 이 슬라이드의 핵심은
"서비스 코드는 의도만 표현하고,
실제 datasource 전환은 공통 모듈이 처리한다"입니다.

### 마무리 멘트

정리하면,
1번 슬라이드에서는 공통 인프라가 routing datasource 구조를 만든다는 점을 설명했고,
2번 슬라이드에서는 READ/WRITE가 결정되는 규칙을 설명했고,
3번 슬라이드에서는 `@UseWriteDataSource`가 실제 서비스 코드에서 어떤 예외 경로를 만드는지 보여줬습니다.

결국 이 구조의 장점은
기본적으로는 readOnly 조회를 read DB로 보내서 부하를 분산하고,
필요한 경우에만 `@UseWriteDataSource`로 writer DB를 선택해서 최신성을 확보할 수 있다는 점입니다.
즉, 성능과 데이터 최신성을 상황에 맞게 둘 다 가져가기 위한 구조라고 보시면 됩니다.

## 짧은 발표 버전

1번 슬라이드는 공통 인프라 구조입니다.
애플리케이션은 `routingDataSource` 하나만 사용하고,
그 안에서 `TransactionRoutingDataSource`가 read와 write를 나눕니다.

2번 슬라이드는 라우팅 규칙입니다.
기본은 `readOnly -> READ`이고,
`@UseWriteDataSource`가 붙으면 그보다 우선해서 WRITE를 강제합니다.

3번 슬라이드는 실제 예시입니다.
서비스는 annotation으로 의도만 표현하고,
실제 datasource 전환은 Aspect와 RoutingDataSource가 공통으로 처리합니다.

## 발표자 메모

- 1번 슬라이드는 구조 설명이다.
- 2번 슬라이드는 규칙 설명이다.
- 3번 슬라이드는 실제 적용 예시 설명이다.
- "기본은 READ 분리, 필요할 때만 WRITE 강제"라는 문장을 반복하면 메시지가 선명해진다.
