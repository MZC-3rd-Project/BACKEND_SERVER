# Module: `libs/data/entity`

## 한눈에 보기
- 역할: JPA BaseEntity 및 감사 필드 설정을 제공합니다.
- 사용 시점: 엔티티 공통 컬럼(createdAt/updatedAt 등) 재사용 시 사용합니다.
- 추가 기능: read/write DataSource 라우팅을 공통 모듈에서 제공합니다.

## 모듈이 필요한 이유
공통 기능을 서비스마다 다시 만들면 구현이 조금씩 달라지고 유지보수 포인트가 급격히 늘어납니다.
이 모듈은 팀 공통 정책을 한 곳으로 모아 "중복 제거 + 일관성 유지 + 변경 비용 절감"을 만드는 목적입니다.

## 적용 순서
1. `build.gradle.kts`에 모듈 의존성을 추가합니다.
2. 필요한 설정 키를 `application.yml`에 채웁니다.
3. 기존 중복 코드를 모듈 API로 대체합니다.

## 간단 예시
```text
implementation(project(":libs:data:entity"))
public class Item extends BaseEntity { ... }
```

## Read/Write 라우팅 사용법
1. 활성화:
   - `app.datasource.read-write-routing.enabled=true`
   - 비활성화: `app.datasource.read-write-routing.enabled=false` (기본값)
2. Read DB 설정(선택):
   - `app.datasource.read.url`
   - `app.datasource.read.username`
   - `app.datasource.read.password`
   - 미설정 시 read 요청도 write DB로 폴백됩니다.
3. 라우팅 규칙:
   - 기본: write DB
   - `@Transactional(readOnly = true)`: read DB
   - `@UseWriteDataSource`: 강제로 write DB

```java
import com.example.data.entity.datasource.UseWriteDataSource;

@UseWriteDataSource
@Transactional(readOnly = true)
public ItemDetail getLatestSnapshot(Long itemId) {
    ...
}
```
