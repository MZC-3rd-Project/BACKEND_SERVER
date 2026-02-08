# Contributing

## 브랜치 네이밍

```
{type}/{issue-number}-{description}
```

| Type | 용도 | 예시 |
|------|------|------|
| `epic` | EPIC 단위 작업 | `epic/685-booking-stabilization` |
| `story` | STORY 단위 작업 | `story/799-ci-pipeline` |
| `task` | 세부 태스크 | `task/661-env-var-standardization` |
| `fix` | 버그 수정 | `fix/test-failures` |

## 커밋 컨벤션

```
{type}(#{issue}): {설명}
```

### Type

| Type | 설명 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `chore` | 빌드/설정 변경 |
| `ci` | CI/CD 변경 |
| `docs` | 문서 |
| `test` | 테스트 |

### 예시

```
feat(#686): cancel() userId NPE 버그 수정
refactor(#813): Gradle Version Catalog 도입 및 의존성 버전 통일
ci(#799): GitHub Actions CI 빌드/테스트 파이프라인 구축
```

## PR 규칙

1. `dev` 브랜치 대상으로 PR 생성
2. CI (Build & Test, Code Style) 통과 필수
3. PR 본문에 `Closes #{issue-number}` 포함
4. 제목은 커밋 컨벤션과 동일한 형식 사용

## 코드 스타일

- Spotless + Google Java Format 자동 적용
- PR 전 `./gradlew spotlessApply` 실행

## 빌드 확인

PR 전 로컬에서 빌드 확인:

```bash
./gradlew build -x spotlessJava -x spotlessJavaCheck -x spotlessCheck
./gradlew spotlessCheck
```
