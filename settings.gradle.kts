rootProject.name = "project03-backend"

// 공통 모듈
include(":libs:core:exception")
include(":libs:core:util")
include(":libs:api:response")
include(":libs:api:exception-handler")
include(":libs:data:entity")
include(":libs:security:core")

// 게이트웨이
include("servers:gateways:api-gateway")

// 서비스
include("servers:services:auth")
include("servers:services:user")