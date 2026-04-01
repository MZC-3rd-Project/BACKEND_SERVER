# CurrentUserId 다운스트림 전달 구조

파일: [donmoa-current-user-id-downstream-class.puml](./donmoa-current-user-id-downstream-class.puml)

## 장표 제목 예시

- `Gateway에서 Downstream까지 CurrentUserId가 전달되는 구조`
- `@CurrentUserId가 Controller까지 주입되는 과정`

## 장표 핵심 메시지

- Gateway가 사용자 정보를 헤더로 내려준다.
- Downstream 서비스는 공통 보안 모듈에서 이를 검증한다.
- Controller는 `@CurrentUserId`만 선언하면 사용자 식별자를 바로 받을 수 있다.

## 구조 요약

- Gateway의 `SessionHeaderRelayGlobalFilter`가 세션에서 사용자 정보를 꺼낸다.
- 이 값은 `X-Gateway-Context` 또는 `X-User-Id` 헤더로 downstream 서비스에 전달된다.
- downstream 서비스의 `GatewaySecurityValidationFilter`와 `GatewayRequestVerifier`가 헤더를 검증하고 `AuthContextHolder`에 사용자 컨텍스트를 저장한다.
- 마지막으로 `CurrentUserIdArgumentResolver`가 `@CurrentUserId Long userId` 파라미터에 값을 주입한다.

## 다이어그램 읽는 순서

1. `SessionHeaderRelayGlobalFilter`
2. `GatewaySessionPrincipalResolver`
3. `GatewaySecurityValidationFilter`
4. `GatewayRequestVerifier`
5. `AuthContextHolder`
6. `CurrentUserIdArgumentResolver`
7. `CartQueryController`

## 장표 설명 문구

이 구조의 핵심은 사용자 식별자를 컨트롤러에서 직접 헤더로 읽지 않는다는 점이다.
Gateway가 사용자 정보를 신뢰 가능한 헤더로 내려주고, downstream 서비스는 공통 보안 모듈에서 이를 검증한 뒤 `AuthContextHolder`에 넣는다.
그 다음 컨트롤러는 `@CurrentUserId`만 선언하면 되므로, 비즈니스 코드가 헤더 파싱이나 인증 검증 로직을 알 필요가 없다.

## 발표 대사 30초 버전

이 장표는 `CurrentUserId`가 어디서 생기고 어떻게 Controller까지 들어오는지를 보여줍니다.
Gateway의 `SessionHeaderRelayGlobalFilter`가 세션에서 사용자 정보를 꺼내 헤더로 내려주고,
downstream 서비스에서는 `GatewaySecurityValidationFilter`와 `GatewayRequestVerifier`가 그 값을 검증해서 `AuthContextHolder`에 저장합니다.
마지막으로 `CurrentUserIdArgumentResolver`가 `@CurrentUserId Long userId` 파라미터에 값을 주입하기 때문에,
컨트롤러와 서비스 코드는 헤더 파싱 없이 사용자 식별자를 바로 사용할 수 있습니다.

## 발표 대사 1분 버전

이 구조에서 중요한 점은 사용자 식별자 전달을 각 서비스가 직접 구현하지 않는다는 것입니다.
먼저 Gateway의 `SessionHeaderRelayGlobalFilter`가 로그인 세션을 기준으로 사용자 정보를 확인하고,
그 값을 `X-Gateway-Context` 같은 내부 헤더로 downstream 요청에 실어 보냅니다.
그 다음 downstream 서비스에 들어오면 `GatewaySecurityValidationFilter`가 요청을 먼저 검사하고,
실제 검증은 `GatewayRequestVerifier`가 수행합니다.
검증이 끝나면 사용자 정보는 `AuthContextHolder`에 저장되고,
Spring MVC 쪽에서는 `CurrentUserIdArgumentResolver`가 `@CurrentUserId`가 붙은 파라미터를 찾아 이 값을 Long 타입으로 꺼내 주입합니다.
그래서 최종적으로 `CartQueryController` 같은 컨트롤러는 인증 헤더를 직접 읽지 않고도 `userId`를 안전하게 받을 수 있습니다.
즉, 보안 검증은 공통 모듈로 모으고, 비즈니스 코드는 `@CurrentUserId`만 사용하는 구조로 분리한 것입니다.

## 발표 대사 상세 버전

이 장표는 `CurrentUserId`가 downstream 서비스의 컨트롤러 파라미터까지 어떻게 전달되는지를 클래스 기준으로 보여줍니다.
왼쪽은 Gateway이고, 가운데는 downstream 서비스의 공통 보안 모듈, 오른쪽은 실제 컨트롤러 예시입니다.

먼저 가장 왼쪽의 `SessionHeaderRelayGlobalFilter`를 보시면,
이 필터가 Gateway에서 요청을 가로채고 현재 로그인한 사용자를 확인합니다.
이때 직접 세션을 읽는 로직은 `GatewaySessionPrincipalResolver`가 맡고 있고,
이 Resolver가 최종적으로 `GatewaySessionPrincipal`을 반환합니다.
여기 안에 우리가 필요한 `userId`와 역할 정보가 들어 있습니다.

그 다음 `SessionHeaderRelayGlobalFilter`는 이 사용자 정보를 downstream으로 그대로 넘길 수 있는 형태로 바꿉니다.
단순한 형태로는 `X-User-Id` 헤더를 넣을 수 있고,
서명된 컨텍스트를 사용하는 경우에는 `GatewayContextHeaderCodec`을 통해 `X-Gateway-Context`를 생성해서 함께 전달합니다.
즉, Gateway 단계의 역할은 "현재 로그인 사용자를 식별하고, downstream이 검증 가능한 헤더 형태로 바꿔서 전달하는 것"입니다.

이제 가운데 downstream 보안 모듈을 보시면,
요청이 서비스에 들어왔을 때 제일 먼저 `GatewaySecurityValidationFilter`가 동작합니다.
이 필터는 이 요청이 gateway에서 온 정상적인 내부 요청인지,
그리고 사용자 컨텍스트가 필요한 요청이라면 사용자 헤더가 제대로 들어왔는지를 확인합니다.
실제 검증 로직 자체는 `GatewayRequestVerifier`가 담당합니다.

`GatewayRequestVerifier`는 전달받은 `X-Gateway-Context` 또는 레거시 `X-User-Id` 헤더를 해석하고,
값이 유효한지, userId가 정상적인지, 서명이 맞는지를 검증합니다.
검증이 끝나면 이 값은 `AuthContext` 객체로 만들어지고,
`AuthContextHolder`에 저장됩니다.
여기서 중요한 점은 이제부터 아래 계층은 더 이상 HTTP 헤더를 직접 볼 필요가 없다는 점입니다.
사용자 정보가 이미 공통 컨텍스트로 변환된 상태가 됩니다.

그 다음 오른쪽으로 오면 Spring MVC 계층에서 `GatewaySecurityWebMvcConfigurer`가 `CurrentUserIdArgumentResolver`를 등록합니다.
이 Resolver는 컨트롤러 메서드 파라미터를 보다가 `@CurrentUserId`가 붙은 `Long` 타입 파라미터를 만나면 동작합니다.
그리고 `AuthContextHolder`에서 userId를 꺼내 `Long`으로 변환해서 주입합니다.
즉, 컨트롤러 입장에서는 인증 헤더를 읽거나 파싱할 필요 없이,
그냥 `@CurrentUserId Long userId`라고 선언만 하면 됩니다.

맨 오른쪽의 `CartQueryController`는 그 최종 결과를 보여주는 예시입니다.
이 컨트롤러는 userId를 직접 계산하지 않고,
이미 주입된 값을 그대로 서비스 호출에 사용합니다.
그래서 비즈니스 코드가 보안 헤더 형식이나 gateway 검증 방식에 결합되지 않습니다.

정리하면,
Gateway는 사용자 정보를 헤더로 전달하고,
downstream 공통 보안 모듈은 그 헤더를 검증해서 컨텍스트로 바꾸고,
컨트롤러는 `@CurrentUserId`만 사용합니다.
즉, 사용자 식별자 전달 책임과 비즈니스 사용 책임을 분리한 구조라고 보시면 됩니다.

## 발표 대사 상세 버전 2분 이상

이 장표는 `CurrentUserId`가 실제로 어디서 생성되고,
어떤 공통 계층을 거쳐서,
최종적으로 downstream 컨트롤러 메서드 파라미터까지 주입되는지를 보여주는 클래스 다이어그램입니다.
발표를 들으실 때는 왼쪽에서 오른쪽으로 따라오시면 됩니다.

먼저 왼쪽의 Gateway 영역입니다.
여기서 시작점은 `SessionHeaderRelayGlobalFilter`입니다.
이 필터는 외부 요청이 내부 서비스로 전달되기 직전에 실행되며,
현재 요청을 어떤 사용자 요청으로 볼 수 있는지 확인하는 역할을 합니다.
하지만 이 필터가 직접 모든 세션 저장소를 뒤지는 것은 아니고,
사용자 확인은 `GatewaySessionPrincipalResolver`에 위임합니다.
이 Resolver는 보안 컨텍스트나 세션 쿠키, 세션 저장소를 기준으로 사용자를 해석하고,
최종적으로 `GatewaySessionPrincipal`을 돌려줍니다.
이 `GatewaySessionPrincipal` 안에는 `userId`, 역할 정보, 세션 식별자 같은 값이 들어 있습니다.

그 다음 `SessionHeaderRelayGlobalFilter`는 이 principal을 바탕으로 downstream 요청 헤더를 구성합니다.
가장 단순하게는 `X-User-Id` 같은 헤더를 내려줄 수 있고,
보다 안전한 방식으로는 `GatewayContextHeaderCodec`을 사용해서
`userId`, 역할, nonce, timestamp, signature를 하나의 `X-Gateway-Context` 헤더로 인코딩해서 보냅니다.
여기서 핵심은 Gateway가 단순 프록시가 아니라,
"이 요청의 사용자 정보는 이 값이다"라고 내부 서비스가 신뢰할 수 있는 형식으로 전달해 주는 진입점이라는 것입니다.

이제 가운데의 downstream 공통 보안 모듈을 보겠습니다.
요청이 실제 서비스에 들어오면 `GatewaySecurityValidationFilter`가 먼저 동작합니다.
이 필터는 두 가지를 확인합니다.
첫 번째는 이 요청이 gateway에서 온 정상적인 내부 요청인지이고,
두 번째는 사용자 컨텍스트가 필요한 경로라면 사용자 헤더가 올바른지입니다.
즉, downstream 서비스는 각 컨트롤러마다 인증 헤더를 따로 검사하지 않고,
이 공통 필터 하나를 통해 입구에서 먼저 걸러냅니다.

실제 헤더 해석과 검증은 `GatewayRequestVerifier`가 맡습니다.
이 클래스는 `X-Gateway-Context`가 오면 그것을 decode하고,
필요하면 레거시 `X-User-Id` 헤더 방식도 처리합니다.
이 과정에서 userId가 양수인지, 서명이 유효한지, 헤더 형식이 올바른지 확인합니다.
검증이 성공하면 사용자 정보는 `AuthContext` 객체로 정리되고,
`AuthContextHolder`에 저장됩니다.
즉, HTTP 헤더에 있던 인증 정보를 이제 애플리케이션 내부에서 쓰기 쉬운 형태의 컨텍스트로 변환하는 것입니다.

이 지점이 중요한 이유는,
이 아래 계층부터는 더 이상 헤더 이름이나 서명 방식 같은 인프라 세부사항을 알 필요가 없기 때문입니다.
컨트롤러, 서비스, 도메인 로직은 그냥 "현재 요청 사용자 정보가 컨텍스트에 있다"는 사실만 사용하면 됩니다.

그 다음 오른쪽의 MVC 바인딩 단계로 넘어갑니다.
`GatewaySecurityWebMvcConfigurer`가 `CurrentUserIdArgumentResolver`를 등록해 두면,
Spring MVC는 컨트롤러 호출 전에 파라미터를 해석할 때 이 Resolver를 사용합니다.
`CurrentUserIdArgumentResolver`는 메서드 파라미터에 `@CurrentUserId`가 붙어 있는지 확인하고,
붙어 있다면 `AuthContextHolder`에서 userId를 꺼내 `Long` 타입으로 변환해서 넣어 줍니다.
필수 파라미터인데 값이 없으면 예외를 던지고,
선택 파라미터라면 null 처리도 가능하게 설계되어 있습니다.

마지막 오른쪽의 `CartQueryController`는 실제 사용 예시입니다.
이 컨트롤러는 `@CurrentUserId Long userId`라고만 선언하면 되고,
그 값은 이미 앞 단계에서 검증과 변환을 거쳐 들어오게 됩니다.
그래서 컨트롤러는 헤더에서 값을 꺼내거나 숫자 파싱을 할 필요가 없고,
보안 헤더가 어떤 이름인지도 몰라도 됩니다.
결과적으로 컨트롤러와 서비스 코드는 비즈니스 로직에만 집중할 수 있습니다.

이 구조를 한 문장으로 정리하면,
Gateway가 사용자 정보를 실어 보내고,
downstream 공통 보안 모듈이 그것을 검증해 컨텍스트로 바꾸고,
컨트롤러는 `@CurrentUserId`로 그 결과만 받아 쓰는 구조입니다.
즉, 인증 정보 전달 책임은 공통 계층에 모으고,
비즈니스 계층은 사용자 식별자를 단순한 파라미터처럼 사용하도록 분리한 설계라고 보시면 됩니다.

## 장표에서 강조할 포인트

- 컨트롤러는 헤더를 직접 읽지 않는다.
- 인증 검증은 downstream 공통 모듈이 담당한다.
- `@CurrentUserId`는 `AuthContextHolder`에 저장된 값을 꺼내는 마지막 단계다.
- 요청 종료 시 `AuthContextCleanupFilter`가 ThreadLocal을 정리해 컨텍스트 누수를 막는다.

## 발표자 메모

- 클래스 다이어그램은 왼쪽에서 오른쪽으로 읽는다고 먼저 말하면 청중이 따라오기 쉽다.
- `SessionHeaderRelayGlobalFilter`와 `CurrentUserIdArgumentResolver`만 먼저 짚고, 중간 검증 계층은 두 번째 문장에 설명하면 이해가 빠르다.
- 구현 세부보다 "Gateway에서 넣고, downstream에서 검증하고, controller에 주입한다"는 3단 구조를 반복해서 말하는 편이 좋다.
