package com.example.openapi;

import com.example.contracts.http.HttpHeaderNames;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

public class CommonHeaderOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        operation.addParametersItem(new Parameter()
                .in("header")
                .name(HttpHeaderNames.USER_ID)
                .description("인증된 사용자 ID (게이트웨이에서 자동 주입)")
                .required(false)
                .schema(new StringSchema()));

        operation.addParametersItem(new Parameter()
                .in("header")
                .name(HttpHeaderNames.USER_ROLES)
                .description("사용자 역할 (게이트웨이에서 자동 주입)")
                .required(false)
                .schema(new StringSchema()));

        return operation;
    }
}
