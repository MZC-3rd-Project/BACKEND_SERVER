rootProject.name = "project03-backend"

// 공통 모듈
include("libs:core")
include("libs:api")
include("libs:data")
include("libs:security")

// 게이트웨이
include("servers:gateways:api-gateway")

// 서비스
include("servers:services:auth")
include("servers:services:user")