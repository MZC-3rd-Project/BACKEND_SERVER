# Nginx PR Performance Measurement

이 저장소는 EKS + ALB + `client-gateway` 런타임을 기준으로 운영되지만, 성능 측정 자산은 Nginx가 ALB 뒤나 앞에서 트래픽을 받는 경우에도 그대로 쓸 수 있게 만들었다. 핵심은 Nginx가 붙은 실제 엔드포인트를 `BASE_URL`로 주고, PR 전후를 같은 부하로 비교하는 것이다.

## 1. 실험 목표

이번 PR의 효과는 두 축으로 나눠서 본다.

- 커넥션 관리: keepalive, upstream connection reuse, burst 처리 시 tail latency 개선 여부
- 응답 압축: 응답 바이트 절감, p95/p99 유지 여부, Nginx CPU 증가량

가능하면 아래 네 개의 실험군으로 나누는 편이 가장 명확하다.

- `baseline`: 기존 설정
- `connection-only`: 커넥션 관리만 켠 설정
- `compression-only`: 응답 압축만 켠 설정
- `combined`: 둘 다 켠 설정

PR이 이미 두 기능을 함께 묶고 있고 개별 토글이 어렵다면 최소한 `baseline` vs `combined`는 반드시 비교한다.

## 2. 측정 대상 고르기

두 종류의 엔드포인트를 준비한다.

- `SMALL_PATHS`: 가볍고 짧은 요청. 커넥션 관리 효과를 보기 좋다.
- `LARGE_PATHS`: JSON payload가 큰 요청. 압축 효과를 보기 좋다.

추천 기준은 다음과 같다.

- `SMALL_PATHS`
  - 인증이 필요 없거나 쉽게 재현 가능한 엔드포인트
  - fan-out이 적고 빠르게 끝나는 요청
  - 예: `/actuator/health`, 가벼운 목록 조회 API
- `LARGE_PATHS`
  - 응답 본문이 수십 KB 이상 나오는 API
  - 필터/정렬/페이징 조건이 고정 가능한 API
  - 예: 상품 목록, 상세 응답, 대시보드 요약 API

## 3. 실행 자산

- k6 시나리오: [deploy/performance/k6/nginx-pr-benchmark.js](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/deploy/performance/k6/nginx-pr-benchmark.js)
- k6 결과 비교기: [deploy/performance/summarize-k6-results.rb](/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject/deploy/performance/summarize-k6-results.rb)

`nginx-pr-benchmark.js`는 세 가지 시나리오를 지원한다.

- `small`: 가벼운 요청을 일정 비율로 계속 보낸다.
- `large`: 큰 응답을 일정 비율로 계속 보낸다.
- `burst`: 짧은 요청에 순간적인 증가 부하를 준다.

## 4. 실행 예시

결과 파일을 저장할 디렉터리를 먼저 만든다.

```bash
mkdir -p /tmp/nginx-pr-results
```

### 4-1. 커넥션 관리 측정

`small`과 `burst`를 같이 돌려서 p95/p99, connecting time, 실패율을 본다.

```bash
k6 run \
  --summary-export /tmp/nginx-pr-results/baseline-connection.json \
  -e BASE_URL=https://api.dev.example.com \
  -e SCENARIOS=small,burst \
  -e SMALL_PATHS=/actuator/health,/api/v1/products?page=0&size=1 \
  -e SMALL_RATE=60 \
  -e BURST_START_RATE=20 \
  -e BURST_STAGES=30s:20,1m:80,30s:120,30s:20 \
  deploy/performance/k6/nginx-pr-benchmark.js
```

PR 적용 환경에서 같은 명령으로 다시 실행한다.

```bash
k6 run \
  --summary-export /tmp/nginx-pr-results/pr-connection.json \
  -e BASE_URL=https://pr-api.dev.example.com \
  -e SCENARIOS=small,burst \
  -e SMALL_PATHS=/actuator/health,/api/v1/products?page=0&size=1 \
  -e SMALL_RATE=60 \
  -e BURST_START_RATE=20 \
  -e BURST_STAGES=30s:20,1m:80,30s:120,30s:20 \
  deploy/performance/k6/nginx-pr-benchmark.js
```

### 4-2. 응답 압축 측정

`large`만 돌려서 `data_received`, compressed rate, latency 변화를 본다.

```bash
k6 run \
  --summary-export /tmp/nginx-pr-results/baseline-compression.json \
  -e BASE_URL=https://api.dev.example.com \
  -e SCENARIOS=large \
  -e LARGE_PATHS=/api/v1/products?page=0&size=50,/api/v1/performances?page=0&size=50 \
  -e LARGE_RATE=20 \
  -e ACCEPT_ENCODING=gzip \
  deploy/performance/k6/nginx-pr-benchmark.js
```

PR 적용 환경도 같은 부하로 실행한다.

```bash
k6 run \
  --summary-export /tmp/nginx-pr-results/pr-compression.json \
  -e BASE_URL=https://pr-api.dev.example.com \
  -e SCENARIOS=large \
  -e LARGE_PATHS=/api/v1/products?page=0&size=50,/api/v1/performances?page=0&size=50 \
  -e LARGE_RATE=20 \
  -e ACCEPT_ENCODING=gzip \
  deploy/performance/k6/nginx-pr-benchmark.js
```

### 4-3. 결과 요약

```bash
ruby deploy/performance/summarize-k6-results.rb \
  --baseline /tmp/nginx-pr-results/baseline-connection.json \
  --candidate /tmp/nginx-pr-results/pr-connection.json \
  --baseline-label baseline \
  --candidate-label pr
```

```bash
ruby deploy/performance/summarize-k6-results.rb \
  --baseline /tmp/nginx-pr-results/baseline-compression.json \
  --candidate /tmp/nginx-pr-results/pr-compression.json \
  --baseline-label baseline \
  --candidate-label pr
```

## 5. 같이 수집해야 하는 운영 지표

애플리케이션 성과 해석은 `k6`만으로 끝내지 말고, Nginx와 업스트림 자원도 같이 본다.

- Nginx
  - CPU, memory
  - active connections, waiting connections
  - 4xx, 5xx
  - request rate
  - egress bytes
- 업스트림 애플리케이션
  - CPU, memory
  - pod restart
  - p95/p99 latency
  - backend 5xx

Nginx access log에 아래 필드가 있으면 해석이 쉬워진다.

```nginx
log_format perf escape=json
  '{"time":"$time_iso8601",'
  '"status":$status,'
  '"method":"$request_method",'
  '"uri":"$uri",'
  '"request_time":$request_time,'
  '"upstream_connect_time":"$upstream_connect_time",'
  '"upstream_header_time":"$upstream_header_time",'
  '"upstream_response_time":"$upstream_response_time",'
  '"bytes_sent":$bytes_sent,'
  '"body_bytes_sent":$body_bytes_sent,'
  '"request_length":$request_length,'
  '"gzip_ratio":"$gzip_ratio",'
  '"connection":$connection,'
  '"connection_requests":$connection_requests}';
```

여기서 특히 중요한 건 아래다.

- 커넥션 관리
  - `upstream_connect_time`
  - `connection_requests`
  - active/waiting connections
  - p95/p99 latency
- 응답 압축
  - `bytes_sent`
  - `body_bytes_sent`
  - `gzip_ratio`
  - Nginx CPU

## 6. 해석 기준

실무적으로는 다음 기준이 있으면 의사결정이 쉽다.

| 항목 | 기대 방향 |
| --- | --- |
| p95 / p99 latency | 같거나 감소 |
| 5xx rate | 증가하지 않아야 함 |
| avg connecting time | 감소 |
| compressed response rate | 증가 |
| data received rate | 감소 |
| Nginx CPU | 증가하더라도 감내 가능한 범위여야 함 |

압축 PR은 아래처럼 해석한다.

- `data_received`가 충분히 감소했다.
- `compressed_response_rate`가 높다.
- p95/p99가 유지된다.
- CPU 증가가 허용 범위 안이다.

커넥션 관리 PR은 아래처럼 해석한다.

- `http_req_connecting.avg`가 줄었다.
- burst 구간 p95/p99가 개선됐다.
- 5xx와 타임아웃이 늘지 않았다.

## 7. 반복 측정 팁

- 워밍업 2분, 본 측정 5분 이상으로 잡는다.
- 각 실험군은 최소 3회 반복한다.
- 같은 시간대, 같은 트래픽 조건, 같은 응답 데이터 조건으로 비교한다.
- CDN, 브라우저 캐시, A/B 라우팅 영향은 제거한다.
- 가능하면 PR 전후를 같은 노드 수와 같은 autoscaling 상태에서 비교한다.

## 8. 결과 정리 템플릿

아래 표 하나면 리뷰어 설득에는 충분하다.

| 실험 | 지표 | baseline | PR | 해석 |
| --- | --- | ---: | ---: | --- |
| connection | p95 latency |  |  |  |
| connection | p99 latency |  |  |  |
| connection | avg connecting time |  |  |  |
| connection | 5xx rate |  |  |  |
| compression | compressed response rate |  |  |  |
| compression | data received rate |  |  |  |
| compression | p95 latency |  |  |  |
| compression | Nginx CPU |  |  |  |
