# DonMoa Telemetry / Observability PPT 발표 대본

이 문서는 DonMoa 프로젝트의 텔레메트리 파트를 발표할 때 바로 읽을 수 있는 발표 대본이다.
이 프로젝트 내부 문서와 실제 코드 기준으로 작성했다.

권장 발표 시간:

- 4분 버전: 슬라이드 5장
- 6분 버전: 슬라이드 6장

권장 장표:

- `deploy/diagrams/donmoa-observability-architecture.drawio`
- `deploy/diagrams/donmoa-observability-implementation.drawio`
- `deploy/diagrams/donmoa-observability-code-proof.drawio`
- 필요하면 `deploy/diagrams/donmoa-logging-standard.drawio`

발표에서 사용할 용어:

- 면접이나 수업 발표에서는 `텔레메트리`라고 말해도 된다.
- 프로젝트 문서와 다이어그램 이름은 대부분 `observability`로 되어 있다.
- 발표에서는 "`텔레메트리, 즉 observability 체계`"라고 한 번만 정리하고 이후에는 편한 용어 하나로 통일하면 된다.

## 슬라이드 1. 왜 텔레메트리가 필요했는가

슬라이드 핵심 메시지:

- DonMoa는 gateway와 여러 도메인 서비스로 분리된 MSA 구조다.
- 장애가 나면 로그 한 줄만 보는 방식으로는 원인 구간을 빠르게 좁히기 어렵다.
- 그래서 메트릭, 로그, 트레이스를 함께 보는 텔레메트리 체계를 만들었다.

발표 대본:

안녕하세요. 이 장에서는 DonMoa 프로젝트에서 텔레메트리, 즉 observability를 어떻게 설계하고 구현했는지 설명드리겠습니다.
저희 프로젝트는 gateway와 여러 도메인 서비스로 나뉜 마이크로서비스 구조라서, 장애가 발생했을 때 어느 서비스에서 병목이 생겼는지 빠르게 확인하는 체계가 꼭 필요했습니다.
단순히 로그를 많이 남기는 것만으로는 부족했고, 요청량과 지연 시간 같은 메트릭, 실제 로그, 그리고 요청 흐름을 따라가는 trace를 함께 연결해서 보도록 구성했습니다.

짧게 말하는 20초 버전:

DonMoa는 MSA 구조이기 때문에 장애 시 원인을 한 번에 찾기 어렵습니다.
그래서 저희는 메트릭, 로그, 트레이스를 분리해서 보지 않고 한 요청 흐름으로 연결해서 볼 수 있는 텔레메트리 체계를 구축했습니다.

## 슬라이드 2. 전체 수집 구조

추천 장표:

- `deploy/diagrams/donmoa-observability-architecture.drawio`

슬라이드 핵심 메시지:

- 메트릭은 Spring Boot Actuator와 Prometheus 기반으로 수집한다.
- 로그는 JSON stdout으로 통일하고 Alloy가 Loki로 보낸다.
- trace 정보는 공통 tracing 모듈이 MDC에 넣어 로그와 연결한다.
- Grafana에서 메트릭과 로그를 한 흐름으로 탐색한다.

발표 대본:

전체 구조는 크게 세 갈래입니다.
첫 번째는 메트릭입니다. 각 서비스는 `/actuator/prometheus` 엔드포인트를 열고, Prometheus가 이를 주기적으로 scrape해서 저장합니다.
두 번째는 로그입니다. 애플리케이션 로그와 Nginx access log를 JSON 형식으로 stdout에 남기고, Grafana Alloy가 이를 수집해서 Loki로 전송합니다.
세 번째는 trace입니다. 공통 tracing 모듈이 `traceId`와 `spanId`를 MDC에 넣어주기 때문에, 로그 안에서 같은 요청 흐름을 따라갈 수 있습니다.
결과적으로 운영자는 Grafana에서 "지연 시간이 튄 시점의 메트릭"을 보고, 바로 같은 시간대의 로그와 traceId를 연결해서 원인을 좁힐 수 있습니다.

짧게 말하는 30초 버전:

저희 텔레메트리 구조는 메트릭은 Prometheus, 로그는 Alloy와 Loki, 시각화는 Grafana로 나뉩니다.
그리고 tracing 모듈이 traceId와 spanId를 로그에 남겨서, 단순 모니터링이 아니라 요청 단위 상관관계 조회가 가능하도록 만들었습니다.

## 슬라이드 3. 공통화한 이유와 공통 모듈

추천 장표:

- `deploy/diagrams/donmoa-observability-implementation.drawio`

슬라이드 핵심 메시지:

- 서비스마다 제각각 붙이면 운영 기준이 흔들린다.
- 그래서 metrics, logging, tracing을 공통 라이브러리로 묶었다.
- gateways와 services 하위 모듈에 공통 의존성으로 적용했다.

발표 대본:

이 구조에서 중요한 점은 텔레메트리를 각 서비스가 따로 구현하지 않았다는 점입니다.
저희는 `libs/config/metrics`, `libs/config/logging`, `libs/config/tracing` 모듈을 공통 라이브러리로 만들고, gateway와 service 하위 모듈 전체에 공통 의존성으로 연결했습니다.
이렇게 한 이유는 간단합니다.
서비스마다 로그 포맷, 메트릭 태그, trace 전파 방식이 달라지면 나중에 Grafana에서 한 기준으로 비교하기가 어렵기 때문입니다.
즉 텔레메트리는 기능이 아니라 운영 표준이라고 보고, 공통 모듈로 강제한 것이 핵심입니다.

강조 포인트 한 문장:

저희는 텔레메트리를 개별 기능이 아니라 플랫폼 표준으로 보고 공통 모듈로 묶었습니다.

## 슬라이드 4. 로그와 트레이스 상관관계

추천 장표:

- `deploy/diagrams/donmoa-logging-standard.drawio`
- 없으면 `deploy/diagrams/donmoa-observability-code-proof.drawio`

슬라이드 핵심 메시지:

- 모든 애플리케이션 로그는 JSON stdout으로 출력한다.
- 공통 키는 `requestId`, `traceId`, `spanId`다.
- Servlet과 WebFlux를 모두 지원해서 gateway와 서비스 양쪽에서 requestId를 유지한다.
- Loki label은 최소화해서 cardinality 폭증을 막는다.

발표 대본:

로그 쪽에서 저희가 가장 신경 쓴 부분은 "많이 남기는 것"이 아니라 "같은 기준으로 남기는 것"입니다.
공통 logback 설정으로 모든 애플리케이션 로그를 JSON stdout 형식으로 출력하게 했고, 서비스 이름과 배포 환경, 로그 타입 같은 공통 필드를 항상 넣도록 했습니다.
또한 requestId는 Servlet 필터와 WebFlux 필터를 모두 제공해서 gateway와 일반 서비스 양쪽에서 일관되게 유지되도록 했습니다.
trace가 활성화된 경우에는 `traceId`와 `spanId`도 MDC에 들어가기 때문에, Grafana에서 특정 요청을 기준으로 로그를 이어서 볼 수 있습니다.
반대로 Loki label은 최소화했습니다.
`requestId`나 `traceId`를 label로 올리면 cardinality가 급격히 커져서 운영성이 나빠지기 때문에, 이런 값은 payload에서 검색하도록 설계했습니다.

질문 대비 한 줄 답변:

왜 requestId와 traceId를 label로 안 썼냐고 물으면, 검색 키이긴 하지만 label로 올리면 Loki 운영 비용과 cardinality가 급증하기 때문이라고 답하면 됩니다.

## 슬라이드 5. 메트릭 수집과 실제 커스텀 메트릭

추천 장표:

- `deploy/diagrams/donmoa-observability-implementation.drawio`

슬라이드 핵심 메시지:

- 공통 metrics 모듈이 `service_name`, `env`, `cluster` 태그를 붙인다.
- HTTP 요청 메트릭은 히스토그램과 SLO 구간을 같이 기록한다.
- 각 도메인별로 운영에 필요한 커스텀 메트릭을 추가했다.

발표 대본:

메트릭은 단순히 Actuator 기본값만 노출한 것이 아니라, 공통 태그와 도메인 메트릭을 함께 설계했습니다.
공통 metrics 모듈은 모든 메트릭에 `service_name`, `env`, `cluster` 태그를 붙여서 서비스별 비교가 가능하게 했고, `http.server.requests`와 gateway 요청 메트릭에는 히스토그램과 SLO 구간을 적용해서 p95 같은 지표를 바로 볼 수 있게 했습니다.
그리고 서비스별로 실제 운영 포인트를 측정하는 커스텀 메트릭도 넣었습니다.
예를 들어 media-worker는 작업 성공, 실패, 재시도, DLQ 이동, 처리 지연 시간을 메트릭으로 기록합니다.
client-gateway는 catalog 조회 지연 시간, downstream 오류, fallback 발생 횟수를 기록합니다.
notification 서비스는 영구 실패, 재시도 예약, dropped 건수를 카운터로 남깁니다.
즉 저희 메트릭은 단순 서버 상태가 아니라, 실제 서비스 장애와 연결되는 운영 신호를 담고 있습니다.

짧게 말하는 25초 버전:

공통 메트릭 모듈로 서비스명과 환경 태그를 통일했고, HTTP 지연 시간은 p95까지 볼 수 있게 히스토그램으로 수집했습니다.
그리고 media-worker DLQ, gateway fallback, notification delivery failure처럼 실제 장애 대응에 필요한 커스텀 메트릭을 추가했습니다.

## 슬라이드 6. 운영 활용과 알림

추천 장표:

- `deploy/diagrams/donmoa-observability-implementation.drawio`
- `deploy/diagrams/donmoa-observability-code-proof.drawio`

슬라이드 핵심 메시지:

- 텔레메트리는 수집에서 끝나지 않고 alert까지 연결돼야 한다.
- Grafana alert rules로 운영 이벤트를 감지한다.
- 실제로 5xx 급증, 애플리케이션 에러 버스트, notification 영구 실패, media-worker DLQ를 감지한다.

발표 대본:

마지막으로 중요한 것은, 텔레메트리를 "보는 도구"에서 끝내지 않고 "운영 액션"으로 연결했다는 점입니다.
저희는 Grafana alert rules를 적용해서 단순 CPU 경고가 아니라 서비스 운영 이슈를 바로 감지하도록 구성했습니다.
예를 들어 edge nginx 5xx 급증, 애플리케이션 warning/error burst, notification 영구 실패, media-worker DLQ 이벤트를 알림 대상으로 삼았습니다.
이렇게 하면 장애가 난 뒤에 로그를 뒤지는 방식이 아니라, 문제가 커지기 전에 어떤 서비스에서 어떤 유형의 이상 신호가 나타났는지 먼저 감지할 수 있습니다.
즉 DonMoa의 텔레메트리 목표는 "로그 저장"이 아니라 "운영 판단 속도 향상"입니다.

마무리 한 문장:

저희는 메트릭, 로그, 트레이스를 따로 붙인 것이 아니라, 운영자가 문제를 빠르게 좁히고 대응할 수 있도록 하나의 탐색 흐름으로 연결했습니다.

## 발표 마무리 멘트

정리하면, DonMoa의 텔레메트리는 공통 모듈 기반으로 표준화했고, Prometheus, Loki, Grafana를 중심으로 메트릭과 로그를 수집했으며, requestId와 traceId를 통해 요청 단위 상관관계를 확보했습니다.
그리고 alert rule까지 연결해서 실제 장애 대응 속도를 높이는 방향으로 구현했습니다.
즉 저희는 텔레메트리를 단순한 관측 기능이 아니라, 운영 자동화와 장애 대응을 위한 핵심 플랫폼 기능으로 봤습니다.

## 1분 압축 버전

DonMoa는 MSA 구조라서 장애 시 원인을 빠르게 좁히는 체계가 필요했습니다.
그래서 메트릭은 Prometheus, 로그는 Alloy와 Loki, 시각화는 Grafana로 구성했고, tracing 모듈을 통해 traceId와 spanId를 로그에 남겼습니다.
또한 requestId를 gateway와 서비스 전체에 공통으로 유지해서 요청 단위 조회가 가능하게 했습니다.
여기에 media-worker DLQ, notification 영구 실패, edge 5xx 급증 같은 실제 운영 알림까지 연결해서, 단순 모니터링이 아니라 장애 대응 속도를 높이는 텔레메트리 체계로 만들었습니다.

## 예상 질문과 답변 포인트

Q. 왜 OpenTelemetry 풀스택으로 한 번에 가지 않았나요?

A. 이 프로젝트는 현실적인 운영 속도를 우선했습니다.
이미 Micrometer 기반 metrics와 tracing 모듈이 있었고, JSON 로그와 requestId, traceId 기준 상관관계를 먼저 안정화하는 것이 더 빠른 가치라고 판단했습니다.
문서에도 OTel 정규화는 다음 단계로 정리돼 있습니다.

Q. 왜 로그를 JSON으로 통일했나요?

A. Grafana Loki에서 서비스별, 요청별로 필드를 기준으로 검색하려면 구조화 로그가 훨씬 유리하기 때문입니다.
또한 Nginx와 애플리케이션 로그를 같은 방식으로 다루기 쉬워집니다.

Q. 이 구조의 핵심 성과를 한 문장으로 말하면 무엇인가요?

A. 장애가 발생했을 때 "어느 서비스의 어떤 요청이 왜 느려졌는가"를 메트릭, 로그, trace 흐름으로 빠르게 좁힐 수 있게 만든 것입니다.

## 발표자가 기억하면 좋은 키워드

- 공통 모듈화
- JSON stdout
- requestId / traceId / spanId
- Actuator Prometheus
- Alloy -> Loki -> Grafana
- Prometheus scrape
- p95 / error burst / DLQ
- 운영 가시성
- 대응 속도 향상
