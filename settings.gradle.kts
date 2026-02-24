rootProject.name = "project03-backend"

// 공통 모듈
include(":libs:core:exception")
include(":libs:core:util")
include(":libs:core:id")
include(":libs:core:pagination")
include(":libs:contracts:http")
include(":libs:clients:media-client")
include(":libs:api:response")
include(":libs:api:exception-handler")
include(":libs:data:entity")
include(":libs:security:context")
include(":libs:security:crypto")
include(":libs:security:security-starter")
include(":libs:security:signature")

// 설정 모듈
include(":libs:config:kafka")
include(":libs:config:lock")
include(":libs:config:lock-redisson")
include(":libs:config:redis")
include(":libs:config:resilience")
include(":libs:config:webclient")
include(":libs:config:tracing")
include(":libs:config:shedlock")

// 이벤트 모듈
include(":libs:event:domain")
include(":libs:event:outbox")

// API 문서
include(":libs:openapi:config")

// 게이트웨이
include("servers:gateways:client-gateway")

// 서비스
include("servers:services:auth")
include("servers:services:user")
include("servers:services:chat")
include("servers:services:search")
include("servers:services:product")
include("servers:services:stock")
include("servers:services:funding")
include("servers:services:notification")
include("servers:services:sales")
include("servers:services:hot-deal")
include("servers:services:media-api")
include("servers:services:media-worker")
include("servers:services:user-123")

// 테스트 서버
include("servers:test:test-server")
