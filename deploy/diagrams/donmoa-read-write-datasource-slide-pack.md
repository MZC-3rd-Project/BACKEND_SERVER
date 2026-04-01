# DB Read/Write 분리와 `@UseWriteDataSource` 발표 자료

## 장표 파일

- [donmoa-read-write-datasource-slide-1-overview.puml](./donmoa-read-write-datasource-slide-1-overview.puml)
- [donmoa-read-write-datasource-slide-2-routing-rule.puml](./donmoa-read-write-datasource-slide-2-routing-rule.puml)
- [donmoa-read-write-datasource-slide-3-use-write-example.puml](./donmoa-read-write-datasource-slide-3-use-write-example.puml)

## 장표 구성 의도

- 1장은 공통 인프라가 어떻게 read/write datasource를 구성하는지 보여준다.
- 2장은 실제로 READ와 WRITE가 어떤 규칙으로 선택되는지 설명한다.
- 3장은 `@UseWriteDataSource`가 서비스 코드에서 어떻게 적용되는지 예시를 보여준다.

## 장표 1. 공통 인프라 구성

파일: [donmoa-read-write-datasource-slide-1-overview.puml](./donmoa-read-write-datasource-slide-1-overview.puml)

핵심 메시지:
- 공통 모듈이 `writeDataSource`, `readDataSource`, `routingDataSource`를 등록한다.
- 애플리케이션은 `routingDataSource`만 쓰고, 내부에서 READ/WRITE가 갈린다.

발표 포인트:
- `ReadWriteDataSourceAutoConfiguration`이 기능을 켜는 시작점이다.
- `routingDataSource`가 `@Primary`로 등록되기 때문에 JPA는 이 라우팅 datasource를 사용한다.
- read DB 설정이 없으면 read도 write로 폴백할 수 있다.

장표 읽는 순서:
1. `ReadWriteDataSourceAutoConfiguration`
2. `writeDataSource`, `readDataSource`, `routingDataSource`
3. `TransactionRoutingDataSource`
4. 하단 노트

발표 대사:

첫 번째 장표는 공통 인프라 구조입니다.
맨 위의 `ReadWriteDataSourceAutoConfiguration`이 시작점이고,
이 설정이 켜지면 아래에 `writeDataSource`, `readDataSource`, `routingDataSource`를 등록합니다.
중요한 점은 애플리케이션이 직접 쓰는 대상이 write나 read가 아니라 `routingDataSource`라는 점입니다.
즉, 서비스나 JPA는 datasource를 하나처럼 사용하지만,
실제로는 그 안에서 `TransactionRoutingDataSource`가 실행 시점에 READ와 WRITE를 나눠 주는 구조입니다.
그리고 오른쪽 노트처럼 read 설정이 없으면 write로 폴백할 수도 있어서,
구조를 단계적으로 적용하기 쉽게 만들었습니다.

## 장표 2. 라우팅 결정 규칙

파일: [donmoa-read-write-datasource-slide-2-routing-rule.puml](./donmoa-read-write-datasource-slide-2-routing-rule.puml)

핵심 메시지:
- 기본은 WRITE다.
- `@Transactional(readOnly = true)`면 READ로 간다.
- 단, `@UseWriteDataSource`가 붙으면 READ 규칙보다 WRITE 강제가 우선한다.

발표 포인트:
- `TransactionRoutingDataSource`는 먼저 `DataSourceRoutingContext`를 본다.
- 강제 경로가 없을 때만 트랜잭션의 readOnly 여부를 본다.
- `UseWriteDataSourceAspect`가 `DataSourceRoutingContext`에 WRITE를 push/pop 하므로 우선순위가 높다.

장표 읽는 순서:
1. `TransactionRoutingDataSource`
2. `DataSourceRoutingContext`
3. `Transactional`
4. `UseWriteDataSourceAspect`
5. 하단 노트

발표 대사:

두 번째 장표는 라우팅 결정 규칙입니다.
가운데의 `TransactionRoutingDataSource`가 실제 분기 기준을 가지고 있습니다.
이 클래스는 먼저 `DataSourceRoutingContext`를 보고,
강제로 지정된 경로가 있으면 그 값을 우선 사용합니다.
이 강제 경로가 없을 때만 `@Transactional(readOnly = true)` 같은 트랜잭션 속성을 보고 READ를 선택합니다.
그리고 둘 다 아니면 기본은 WRITE입니다.
아래의 `UseWriteDataSourceAspect`는 바로 이 강제 경로를 만드는 역할을 합니다.
즉, `@UseWriteDataSource`가 붙은 경우에는 readOnly 조회 안에서도 WRITE를 타게 되고,
그래서 최신성이 더 중요한 조회를 예외적으로 writer DB로 보낼 수 있습니다.

## 장표 3. `@UseWriteDataSource` 적용 예시

파일: [donmoa-read-write-datasource-slide-3-use-write-example.puml](./donmoa-read-write-datasource-slide-3-use-write-example.puml)

핵심 메시지:
- 실제 서비스 코드는 기본적으로 조회 서비스에 `@Transactional(readOnly = true)`를 둔다.
- 그런데 최신성이 더 중요한 조회는 `@UseWriteDataSource`로 writer를 강제할 수 있다.
- 메서드 레벨, 클래스 레벨 둘 다 적용 가능하다.

발표 포인트:
- `ParticipationQueryService`는 클래스는 readOnly지만 특정 메서드에만 `@UseWriteDataSource`를 붙였다.
- `InternalCampaignQueryService`는 클래스 전체에 `@UseWriteDataSource`를 붙였다.
- 이 annotation은 서비스 코드에서 선언하고, 실제 라우팅 전환은 Aspect가 맡는다.

장표 읽는 순서:
1. `ParticipationQueryController`
2. `ParticipationQueryService`
3. `InternalCampaignQueryService`
4. `UseWriteDataSource`
5. `UseWriteDataSourceAspect`
6. `Repository -> TransactionRoutingDataSource`

발표 대사:

세 번째 장표는 실제 코드 예시입니다.
왼쪽의 `ParticipationQueryController`가 요청을 받고,
조회 서비스인 `ParticipationQueryService`로 넘깁니다.
이 서비스는 기본적으로 readOnly 조회 서비스지만,
일부 메서드에 `@UseWriteDataSource`를 붙여서 WRITE를 강제하고 있습니다.
반대로 `InternalCampaignQueryService`는 클래스 레벨에 `@UseWriteDataSource`를 붙였기 때문에
서비스 전체가 writer DB를 사용합니다.
중요한 점은 서비스가 datasource를 직접 바꾸는 것이 아니라,
annotation으로 의도만 표현하고 실제 전환은 `UseWriteDataSourceAspect`와 `TransactionRoutingDataSource`가 처리한다는 점입니다.
즉, 비즈니스 코드는 깔끔하게 유지하면서도 최신성이 필요한 조회만 예외적으로 writer DB를 사용할 수 있습니다.

## 전체 발표 대사 1분 버전

이 구조는 DB를 read와 write로 분리하되,
서비스 코드는 그 복잡한 라우팅을 직접 다루지 않도록 만든 구조입니다.
먼저 공통 모듈이 `writeDataSource`, `readDataSource`, `routingDataSource`를 등록하고,
애플리케이션은 `routingDataSource`만 사용합니다.
실제 분기 기준은 `TransactionRoutingDataSource`가 가지고 있고,
기본은 WRITE, `@Transactional(readOnly = true)`는 READ입니다.
여기에 예외적으로 최신성이 더 필요한 조회는 `@UseWriteDataSource`를 붙여 WRITE를 강제할 수 있습니다.
이 강제는 `UseWriteDataSourceAspect`가 `DataSourceRoutingContext`에 WRITE를 넣는 방식으로 처리합니다.
실제 예시로는 `ParticipationQueryService`처럼 메서드 단위로 적용할 수도 있고,
`InternalCampaignQueryService`처럼 클래스 단위로 적용할 수도 있습니다.

## 전체 발표 대사 상세 버전

이 장표 묶음은 DB read/write 분리 구조와,
그 위에서 `@UseWriteDataSource`가 어떤 역할을 하는지를
구조, 규칙, 예시 순서로 설명하기 위해 준비했습니다.

먼저 1번 장표는 공통 인프라 구성입니다.
여기서 출발점은 `ReadWriteDataSourceAutoConfiguration`입니다.
이 AutoConfiguration은 기능이 활성화되면 write용 datasource와 read용 datasource를 만들고,
그 위에 `TransactionRoutingDataSource`를 얹어서 `routingDataSource`를 `@Primary`로 등록합니다.
그래서 JPA나 Repository 입장에서는 datasource가 하나처럼 보이지만,
실제로는 실행 시점에 read와 write가 갈라지는 구조가 됩니다.
또 read 설정이 따로 없으면 read도 write로 폴백하게 설계되어 있어서,
기능을 점진적으로 켜기 쉽게 되어 있습니다.

2번 장표는 실제 라우팅 규칙입니다.
여기서는 가운데 `TransactionRoutingDataSource`를 중심으로 보시면 됩니다.
이 클래스는 먼저 `DataSourceRoutingContext`를 보고 강제 경로가 있는지를 확인합니다.
강제 경로가 있으면 그 값을 그대로 쓰고,
없을 때만 현재 트랜잭션의 readOnly 여부를 확인해서 READ나 WRITE를 결정합니다.
즉 기본 정책은 "읽기 전용 트랜잭션은 READ, 나머지는 WRITE"입니다.
여기서 예외를 만드는 장치가 `@UseWriteDataSource`이고,
이 annotation이 붙으면 `UseWriteDataSourceAspect`가 실행되어
`DataSourceRoutingContext`에 WRITE를 넣습니다.
그래서 readOnly 조회 안에서도 writer DB를 강제로 선택할 수 있습니다.

3번 장표는 실제 서비스 적용 예시입니다.
`ParticipationQueryService`는 기본적으로 readOnly 조회 서비스입니다.
하지만 `findByUserId`, `findByCampaignId` 같은 특정 메서드에는 `@UseWriteDataSource`를 붙였습니다.
즉, 전체 서비스 기본값은 read DB 조회이지만,
일부 조회는 write DB를 써야 한다는 의도를 코드로 표현한 것입니다.
반대로 `InternalCampaignQueryService`는 클래스 자체에 `@UseWriteDataSource`를 붙였기 때문에
그 안의 모든 조회가 writer DB를 사용합니다.
여기서 중요한 점은 서비스가 datasource를 직접 바꾸지 않는다는 것입니다.
서비스는 annotation으로 의도만 표현하고,
실제 라우팅 전환은 Aspect와 RoutingDataSource가 공통으로 처리합니다.

정리하면,
이 구조는 기본적으로는 readOnly 조회를 read DB로 보내서 부하를 나누고,
필요할 때만 `@UseWriteDataSource`로 writer DB를 선택해 최신성을 확보하도록 만든 구조입니다.
즉, 성능 최적화와 최신성 요구를 코드 레벨에서 같이 제어할 수 있게 한 설계라고 보시면 됩니다.

## 슬라이드 진행 순서에 맞춘 발표 대사 상세 버전

### 1번 슬라이드 보여주고

첫 번째 슬라이드는 공통 인프라 구조입니다.
여기서는 가장 위에 있는 `ReadWriteDataSourceAutoConfiguration`부터 보시면 됩니다.
이 클래스가 read/write 분리 기능을 켜는 시작점이고,
기능이 활성화되면 아래에 `writeDataSource`, `readDataSource`, `routingDataSource`를 등록합니다.

여기서 중요한 포인트는 애플리케이션이 직접 사용하는 대상이
write datasource나 read datasource가 아니라 `routingDataSource`라는 점입니다.
즉, 서비스나 JPA는 datasource를 하나처럼 사용하지만,
실제로는 내부에서 `TransactionRoutingDataSource`가 실행 시점에 어떤 DB로 보낼지 결정합니다.

쉽게 말하면,
개발자는 datasource를 두 개 직접 다루지 않고,
공통 모듈이 중간에서 read와 write를 나눠 주는 구조라고 보시면 됩니다.

그리고 아래 노트를 보시면
실제 JPA와 Repository는 이 routing datasource를 사용하고,
실행 시점에 READ 또는 WRITE가 선택된다고 되어 있습니다.
오른쪽 노트처럼 read 설정이 따로 없으면 write로 폴백할 수도 있어서,
처음부터 복잡한 인프라를 다 갖추지 않아도 점진적으로 적용할 수 있게 만들었습니다.

그래서 1번 슬라이드의 핵심은
"애플리케이션은 routing datasource 하나만 바라보고,
그 안에서 공통 모듈이 read/write를 분리한다"입니다.

### 2번 슬라이드 보여주고

두 번째 슬라이드는 실제로 READ와 WRITE가 어떤 규칙으로 결정되는지 설명하는 장표입니다.
여기서는 가운데 있는 `TransactionRoutingDataSource`를 중심으로 보시면 됩니다.

이 클래스는 먼저 왼쪽의 `DataSourceRoutingContext`를 확인합니다.
이 말은, 누군가가 "이번 호출은 무조건 WRITE를 써라"라고 강제로 지정해 둔 경로가 있는지를 먼저 본다는 뜻입니다.
만약 여기에 값이 들어 있으면 그 값을 가장 우선해서 사용합니다.

그 강제 경로가 없을 때만 아래의 `@Transactional` 정보를 보고 판단합니다.
그래서 기본 규칙은 매우 단순합니다.
`@Transactional(readOnly = true)`면 READ로 보내고,
그 외에는 WRITE로 보냅니다.

그런데 여기서 예외를 만드는 장치가 오른쪽 아래의 `UseWriteDataSourceAspect`입니다.
`@UseWriteDataSource`가 붙은 메서드나 클래스가 실행되면,
이 Aspect가 `DataSourceRoutingContext`에 WRITE를 넣어 줍니다.
그러면 `TransactionRoutingDataSource`는 readOnly 여부를 보기 전에
이미 강제 경로로 WRITE를 발견하게 되고,
결과적으로 readOnly 조회 안에서도 writer DB를 사용하게 됩니다.

즉, 2번 슬라이드의 핵심은
"기본 규칙은 readOnly면 READ지만,
`@UseWriteDataSource`가 붙으면 그 규칙보다 WRITE 강제가 우선한다"입니다.

이 장표를 설명할 때는
가운데 `TransactionRoutingDataSource`,
왼쪽 `DataSourceRoutingContext`,
아래 `Transactional`,
오른쪽 아래 `UseWriteDataSourceAspect`
순서로 짚어 주면 청중이 이해하기 쉽습니다.

### 3번 슬라이드 보여주고

세 번째 슬라이드는 이 규칙이 실제 서비스 코드에서 어떻게 쓰이는지를 보여주는 예시입니다.
왼쪽 위의 `ParticipationQueryController`가 요청을 받고,
그 아래의 `ParticipationQueryService`로 넘깁니다.

이 서비스는 기본적으로 조회 서비스이기 때문에
실제 코드에서는 `@Transactional(readOnly = true)` 기반으로 동작합니다.
그런데 모든 조회가 read DB로 가는 것은 아니고,
일부 메서드에는 `@UseWriteDataSource`를 붙여서 WRITE를 강제하고 있습니다.
즉, 전체 서비스의 기본 정책은 조회이지만,
일부 조회는 최신성이 더 중요해서 writer DB를 보겠다는 의도를 코드에 표현한 것입니다.

옆의 `InternalCampaignQueryService`는 조금 더 강한 예시입니다.
이쪽은 클래스 레벨에 `@UseWriteDataSource`를 붙였기 때문에,
해당 서비스의 조회는 전체적으로 writer DB를 사용하게 됩니다.
즉, 메서드 단위로도 적용할 수 있고,
클래스 단위로도 적용할 수 있다는 점을 보여줍니다.

그리고 중요한 점은,
이 서비스들이 직접 datasource를 선택하거나 전환하지 않는다는 것입니다.
서비스는 단지 `@UseWriteDataSource`라는 annotation으로 의도만 표현합니다.
실제 전환은 가운데의 `UseWriteDataSourceAspect`가 처리하고,
최종적으로는 Repository를 거쳐 `TransactionRoutingDataSource`가 READ/WRITE를 결정합니다.

그래서 3번 슬라이드의 핵심은
"서비스 코드는 의도만 표현하고,
실제 datasource 라우팅은 공통 인프라가 처리한다"입니다.

### 마무리 멘트

정리하면,
1번 슬라이드에서는 공통 인프라가 read/write 분리 구조를 만든다는 점을 보여줬고,
2번 슬라이드에서는 실제 라우팅 규칙이 어떻게 동작하는지를 설명했고,
3번 슬라이드에서는 그 규칙이 서비스 코드에서 `@UseWriteDataSource`로 어떻게 사용되는지를 보여줬습니다.

결국 이 구조의 장점은
기본적으로는 read DB로 조회 부하를 분산하면서도,
최신성이 더 중요한 조회는 `@UseWriteDataSource`로 writer DB를 강제할 수 있다는 점입니다.
즉, 성능과 데이터 최신성을 상황에 맞게 선택할 수 있도록 만든 구조라고 보시면 됩니다.

## 장표에서 강조할 포인트

- 서비스는 datasource를 직접 선택하지 않는다.
- 기본 규칙은 `@Transactional(readOnly = true)`이면 READ다.
- `@UseWriteDataSource`는 예외적으로 WRITE를 강제하는 장치다.
- 메서드 레벨과 클래스 레벨 둘 다 적용 가능하다.
- 공통 모듈이 라우팅을 처리하므로 서비스 코드는 의도만 표현하면 된다.

## 발표자 메모

- 1장에서는 "구조", 2장에서는 "규칙", 3장에서는 "예시"라고 분명히 끊어 말하는 편이 좋다.
- `readOnly인데 왜 write를 쓰냐`는 질문이 나오면 "최신성이 더 중요한 조회를 위한 예외 경로"라고 설명하면 된다. 이 부분은 코드 구조 해석에 기반한 설명이다.
- `UseWriteDataSourceAspect`가 `@annotation`과 `@within` 둘 다 잡는다는 점을 말하면 메서드/클래스 레벨 둘 다 지원한다는 설명이 자연스럽다.
