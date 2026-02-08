# Pagination Module

커서 기반 및 오프셋 기반 페이지네이션 유틸리티를 제공하는 모듈입니다.

## 개요

이 모듈은 RESTful API에서 사용할 수 있는 두 가지 페이지네이션 방식을 제공합니다:

1. **커서 기반 페이지네이션** (권장) - 대용량 데이터 및 실시간 데이터에 적합
2. **오프셋 기반 페이지네이션** - 전통적인 방식, 총 개수 및 페이지 번호가 필요한 경우

## 커서 기반 페이지네이션

### 장점

- 대용량 데이터셋에서도 일관된 성능 (OFFSET 성능 저하 없음)
- 실시간 데이터에서 일관된 결과 (삽입/삭제에도 안정적)
- 무한 스크롤 및 모바일 앱에 적합
- 무상태 커서로 API 요청 제한 처리 용이

### 사용 예시

```java
// 1. 요청 생성
CursorRequest request = CursorRequest.firstPage(20);
// 또는
CursorRequest nextRequest = CursorRequest.of("encodedCursor", 20);

// 2. 데이터 조회 (예시)
List<User> users = userRepository.findAllAfterCursor(
    request.getCursor(),
    request.getSize() + 1  // hasNext 판단을 위해 +1
);

// 3. 다음 커서 생성
String nextCursor = null;
if (users.size() > request.getSize()) {
    users = users.subList(0, request.getSize());
    User lastUser = users.get(users.size() - 1);
    nextCursor = CursorUtils.encode(lastUser.getId());
}

// 4. 응답 생성
CursorResponse<User> response = CursorResponse.of(users, nextCursor);
```

### API 응답 예시

```json
{
  "items": [
    {"id": 1, "name": "User 1"},
    {"id": 2, "name": "User 2"}
  ],
  "nextCursor": "MjA=",
  "hasNext": true,
  "size": 2
}
```

## 오프셋 기반 페이지네이션

### 단점

- 대용량 데이터에서 성능 저하 (OFFSET N이 커질수록 느려짐)
- 실시간 데이터에서 불일치 (아이템이 건너뛰어지거나 중복될 수 있음)
- 무한 스크롤에 부적합

### 사용 예시

```java
// 1. 요청 생성
OffsetPageRequest request = OffsetPageRequest.of(0, 20);

// 2. 데이터 조회
List<User> users = userRepository.findAll(request.getOffset(), request.getSize());
long totalCount = userRepository.count();

// 3. 응답 생성
OffsetPageResponse<User> response = OffsetPageResponse.of(
    users,
    request.getPage(),
    request.getSize(),
    totalCount
);

// Spring Data와의 통합
Page<User> page = userRepository.findAll(PageRequest.of(0, 20));
OffsetPageResponse<User> response = SpringDataPageAdapter.fromPage(page);
```

### API 응답 예시

```json
{
  "items": [
    {"id": 1, "name": "User 1"},
    {"id": 2, "name": "User 2"}
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "hasNext": true,
  "hasPrevious": false
}
```

## 커서 인코딩/디코딩

`CursorUtils`는 커서 값의 Base64 URL-safe 인코딩을 제공합니다.

```java
// Long ID 인코딩
Long userId = 12345L;
String cursor = CursorUtils.encode(userId);  // "MTIzNDU"

// 커서 디코딩
Long decodedId = CursorUtils.decodeLong(cursor);  // 12345L

// 문자열 인코딩 (복합 커서의 경우)
String compositeCursor = "2024-01-01_12345";
String encoded = CursorUtils.encode(compositeCursor);
String decoded = CursorUtils.decode(encoded);
```

## 의존성

```kotlin
dependencies {
    implementation("com.example:libs-core-pagination")
}
```

## 기술 스택

- Java 21
- Spring Boot 3.5.10
- Lombok
- Jackson (JSON 직렬화)
- Spring Data (선택적)

## 권장사항

1. **커서 기반 페이지네이션 사용 케이스**
   - 무한 스크롤
   - 모바일 앱 피드
   - 실시간 업데이트가 있는 데이터
   - 대용량 데이터셋 (10,000+ 레코드)

2. **오프셋 기반 페이지네이션 사용 케이스**
   - 관리자 대시보드 (페이지 번호 필요)
   - 소규모 데이터셋
   - 총 개수 표시가 필수인 경우

3. **커서 구현 팁**
   - 정렬 기준이 되는 필드는 고유해야 함 (예: ID, created_at + ID)
   - 인덱스가 있는 컬럼을 커서로 사용
   - 복합 정렬의 경우 JSON 인코딩 고려
