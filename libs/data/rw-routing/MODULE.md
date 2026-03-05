# Module: `libs/data/rw-routing`

## 한눈에 보기
- 역할: read/write DataSource 라우팅을 제공합니다.
- 사용 시점: 읽기 트랜잭션은 read DB, 쓰기 트랜잭션은 write DB로 분리하고 싶을 때 사용합니다.

## 핵심 제공
- `ReadWriteDataSourceAutoConfiguration`
- `TransactionRoutingDataSource`
- `@UseWriteDataSource`

## 설정 키
```yaml
app:
  datasource:
    read-write-routing:
      enabled: true
```

## 간단 예시
```text
implementation(project(":libs:data:rw-routing"))
@UseWriteDataSource
@Transactional(readOnly = true)
```
