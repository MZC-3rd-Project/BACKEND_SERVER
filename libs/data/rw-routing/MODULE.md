# Module: `libs/data/rw-routing`

## 이 모듈이 해결하는 문제
읽기/쓰기 DB를 분리하려면 DataSource 라우팅 설정이 필요하고, 서비스마다 구현하면 위험합니다.
`rw-routing`은 read/write 라우팅을 공통으로 제공합니다.

## 언제 사용하면 되나요?
- 읽기 트래픽을 read DB로 분리하고 싶을 때
- `@Transactional(readOnly = true)` 기반 라우팅을 쓰고 싶을 때

## 핵심 제공
- `ReadWriteDataSourceAutoConfiguration`
- `TransactionRoutingDataSource`
- `@UseWriteDataSource` (readOnly 트랜잭션에서도 write 강제)

## 최소 설정
```yaml
app:
  datasource:
    read-write-routing:
      enabled: true
```

## 빠른 시작
```text
implementation(project(":libs:data:rw-routing"))
```
