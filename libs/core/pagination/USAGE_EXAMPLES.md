# Pagination Module - Usage Examples

## 목차
1. [커서 기반 페이지네이션 구현](#커서-기반-페이지네이션-구현)
2. [오프셋 기반 페이지네이션 구현](#오프셋-기반-페이지네이션-구현)
3. [Spring Data JPA 통합](#spring-data-jpa-통합)
4. [REST API 컨트롤러 예시](#rest-api-컨트롤러-예시)

---

## 커서 기반 페이지네이션 구현

### Repository 구현

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 커서 기반 조회: ID가 cursor보다 큰 레코드를 가져옴
    @Query("SELECT u FROM User u WHERE u.id > :cursor ORDER BY u.id ASC")
    List<User> findAllAfterCursor(@Param("cursor") Long cursor, Pageable pageable);

    // 첫 페이지 조회
    List<User> findAllByOrderByIdAsc(Pageable pageable);
}
```

### Service 구현

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public CursorResponse<UserDto> getUsers(CursorRequest request) {
        // 1. Size + 1 개를 조회하여 다음 페이지 존재 여부 확인
        int fetchSize = request.getSize() + 1;
        Pageable pageable = PageRequest.of(0, fetchSize);

        List<User> users;
        if (request.isFirstPage()) {
            // 첫 페이지
            users = userRepository.findAllByOrderByIdAsc(pageable);
        } else {
            // 커서 이후 데이터 조회
            Long cursorId = CursorUtils.decodeLong(request.getCursor());
            users = userRepository.findAllAfterCursor(cursorId, pageable);
        }

        // 2. 다음 페이지 존재 여부 확인
        String nextCursor = null;
        if (users.size() > request.getSize()) {
            // 마지막 아이템 제거
            users = users.subList(0, request.getSize());
            // 다음 커서 생성
            User lastUser = users.get(users.size() - 1);
            nextCursor = CursorUtils.encode(lastUser.getId());
        }

        // 3. DTO 변환 및 응답 생성
        List<UserDto> userDtos = users.stream()
                .map(UserDto::from)
                .toList();

        return CursorResponse.of(userDtos, nextCursor);
    }
}
```

### 복합 정렬 커서 (created_at + id)

```java
public class CompositeUserCursor {
    private final LocalDateTime createdAt;
    private final Long id;

    public String encode() {
        String value = createdAt.toString() + ":" + id;
        return CursorUtils.encode(value);
    }

    public static CompositeUserCursor decode(String cursor) {
        String decoded = CursorUtils.decode(cursor);
        String[] parts = decoded.split(":");
        return new CompositeUserCursor(
            LocalDateTime.parse(parts[0]),
            Long.parseLong(parts[1])
        );
    }
}

@Query("SELECT u FROM User u " +
       "WHERE (u.createdAt > :createdAt) OR " +
       "(u.createdAt = :createdAt AND u.id > :id) " +
       "ORDER BY u.createdAt ASC, u.id ASC")
List<User> findAfterCursor(
    @Param("createdAt") LocalDateTime createdAt,
    @Param("id") Long id,
    Pageable pageable
);
```

---

## 오프셋 기반 페이지네이션 구현

### Service 구현

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public OffsetPageResponse<ProductDto> getProducts(OffsetPageRequest request) {
        // 1. Spring Data Pageable 생성
        Pageable pageable = PageRequest.of(
            request.getPage(),
            request.getSize(),
            Sort.by("createdAt").descending()
        );

        // 2. 페이지 조회
        Page<Product> productPage = productRepository.findAll(pageable);

        // 3. DTO 변환
        List<ProductDto> productDtos = productPage.getContent().stream()
                .map(ProductDto::from)
                .toList();

        // 4. 응답 생성
        return OffsetPageResponse.of(
            productDtos,
            productPage.getNumber(),
            productPage.getSize(),
            productPage.getTotalElements()
        );
    }

    // 또는 Spring Data Page를 직접 변환
    public OffsetPageResponse<ProductDto> getProductsSimple(OffsetPageRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<Product> productPage = productRepository.findAll(pageable);

        // Page를 DTO Page로 변환
        Page<ProductDto> dtoPage = productPage.map(ProductDto::from);

        // SpringDataPageAdapter 사용
        return SpringDataPageAdapter.fromPage(dtoPage);
    }
}
```

---

## Spring Data JPA 통합

### Spring Data Page 변환

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public OffsetPageResponse<OrderDto> getOrders(int page, int size) {
        // Spring Data의 Page를 직접 OffsetPageResponse로 변환
        Page<Order> orderPage = orderRepository.findAll(
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );

        // Map to DTO
        Page<OrderDto> dtoPage = orderPage.map(OrderDto::from);

        // Convert using adapter
        return SpringDataPageAdapter.fromPage(dtoPage);
    }
}
```

---

## REST API 컨트롤러 예시

### 커서 기반 API

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/v1/users?cursor=xxx&size=20
     */
    @GetMapping
    public ResponseEntity<CursorResponse<UserDto>> getUsers(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        CursorRequest request = CursorRequest.of(cursor, size);
        CursorResponse<UserDto> response = userService.getUsers(request);
        return ResponseEntity.ok(response);
    }
}

// 응답 예시:
// GET /api/v1/users?size=2
// {
//   "items": [
//     {"id": 1, "name": "Alice", "email": "alice@example.com"},
//     {"id": 2, "name": "Bob", "email": "bob@example.com"}
//   ],
//   "nextCursor": "Mg==",
//   "hasNext": true,
//   "size": 2
// }

// 다음 페이지:
// GET /api/v1/users?cursor=Mg==&size=2
```

### 오프셋 기반 API

```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * GET /api/v1/products?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<OffsetPageResponse<ProductDto>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OffsetPageRequest request = OffsetPageRequest.of(page, size);
        OffsetPageResponse<ProductDto> response = productService.getProducts(request);
        return ResponseEntity.ok(response);
    }
}

// 응답 예시:
// GET /api/v1/products?page=0&size=2
// {
//   "items": [
//     {"id": 1, "name": "Product A", "price": 10000},
//     {"id": 2, "name": "Product B", "price": 20000}
//   ],
//   "page": 0,
//   "size": 2,
//   "totalElements": 100,
//   "totalPages": 50,
//   "hasNext": true,
//   "hasPrevious": false
// }
```

### 하이브리드 API (둘 다 지원)

```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * 커서 기반 (모바일 앱용)
     * GET /api/v1/posts?cursor=xxx&size=20
     */
    @GetMapping
    public ResponseEntity<?> getPosts(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (cursor != null || page == null) {
            // 커서 기반
            CursorRequest request = CursorRequest.of(cursor, size);
            return ResponseEntity.ok(postService.getPostsCursor(request));
        } else {
            // 오프셋 기반
            OffsetPageRequest request = OffsetPageRequest.of(page, size);
            return ResponseEntity.ok(postService.getPostsOffset(request));
        }
    }
}
```

---

## 성능 최적화 팁

### 1. 커서용 복합 인덱스 생성

```sql
-- 단일 정렬 (ID만)
CREATE INDEX idx_user_id ON users(id);

-- 복합 정렬 (created_at + id)
CREATE INDEX idx_user_created_id ON users(created_at, id);
```

### 2. N+1 문제 방지

```java
@EntityGraph(attributePaths = {"author", "category"})
List<Post> findAllByOrderByIdAsc(Pageable pageable);
```

### 3. DTO 프로젝션 사용

```java
// Entity 대신 DTO로 직접 조회
@Query("SELECT new com.example.dto.UserDto(u.id, u.name, u.email) " +
       "FROM User u WHERE u.id > :cursor ORDER BY u.id ASC")
List<UserDto> findDtosAfterCursor(@Param("cursor") Long cursor, Pageable pageable);
```

### 4. 캐싱 전략

```java
@Cacheable(value = "users", key = "#request.cursor + '_' + #request.size")
public CursorResponse<UserDto> getUsers(CursorRequest request) {
    // ...
}
```

---

## 테스트 예시

```java
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void shouldPaginateWithCursor() {
        // Given: 100명의 사용자 생성
        createTestUsers(100);

        // When: 첫 페이지 조회 (20개)
        CursorRequest firstRequest = CursorRequest.firstPage(20);
        CursorResponse<UserDto> firstPage = userService.getUsers(firstRequest);

        // Then: 20개 반환, 다음 페이지 있음
        assertThat(firstPage.getSize()).isEqualTo(20);
        assertThat(firstPage.isHasNext()).isTrue();
        assertThat(firstPage.getNextCursor()).isNotNull();

        // When: 다음 페이지 조회
        CursorRequest secondRequest = CursorRequest.of(
            firstPage.getNextCursor(),
            20
        );
        CursorResponse<UserDto> secondPage = userService.getUsers(secondRequest);

        // Then: 20개 반환, 다음 페이지 있음
        assertThat(secondPage.getSize()).isEqualTo(20);
        assertThat(secondPage.isHasNext()).isTrue();

        // When: 마지막 페이지까지 조회
        CursorRequest lastRequest = CursorRequest.of(
            secondPage.getNextCursor(),
            100
        );
        CursorResponse<UserDto> lastPage = userService.getUsers(lastRequest);

        // Then: 60개 반환 (100 - 40), 다음 페이지 없음
        assertThat(lastPage.getSize()).isEqualTo(60);
        assertThat(lastPage.isHasNext()).isFalse();
        assertThat(lastPage.getNextCursor()).isNull();
    }
}
```
