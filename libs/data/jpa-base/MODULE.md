# Module: `libs/data/jpa-base`

## 이 모듈이 해결하는 문제
엔티티마다 `createdAt`, `updatedAt` 같은 공통 필드를 반복 작성하면 유지보수가 어렵습니다.
`jpa-base`는 공통 BaseEntity와 JPA Auditing 설정을 제공합니다.

## 언제 사용하면 되나요?
- 다수 엔티티에 동일한 감사 컬럼이 필요할 때
- `@CreatedDate`, `@LastModifiedDate`를 공통 정책으로 적용할 때

## 핵심 제공
- `BaseEntity`
- `DataEntityAutoConfiguration` (JPA auditing 활성화)

## 빠른 시작
```text
implementation(project(":libs:data:jpa-base"))
```

```java
public class Item extends BaseEntity { ... }
```
