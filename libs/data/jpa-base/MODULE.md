# Module: `libs/data/jpa-base`

## 한눈에 보기
- 역할: `BaseEntity` 및 JPA Auditing(`createdAt/updatedAt`)을 제공합니다.
- 사용 시점: 공통 엔티티 필드를 재사용하고 감사 컬럼을 자동으로 채울 때 사용합니다.

## 핵심 제공
- `BaseEntity`
- `DataEntityAutoConfiguration` (JPA auditing 활성화)

## 간단 예시
```text
implementation(project(":libs:data:jpa-base"))
public class Item extends BaseEntity { ... }
```
