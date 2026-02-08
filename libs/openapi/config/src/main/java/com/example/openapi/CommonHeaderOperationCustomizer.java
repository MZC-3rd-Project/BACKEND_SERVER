package com.example.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class CommonHeaderOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        operation.addParametersItem(new Parameter()
                .in("header")
                .name("X-User-Id")
                .description("인증된 사용자 ID (게이트웨이에서 자동 주입)")
                .required(false)
                .schema(new StringSchema()));

        operation.addParametersItem(new Parameter()
                .in("header")
                .name("X-User-Roles")
                .description("사용자 역할 (게이트웨이에서 자동 주입)")
                .required(false)
                .schema(new StringSchema()));

        return operation;
    }
}
