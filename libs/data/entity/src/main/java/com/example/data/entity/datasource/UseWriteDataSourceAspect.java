package com.example.data.entity.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UseWriteDataSourceAspect {

    @Around("@annotation(com.example.data.entity.datasource.UseWriteDataSource) || @within(com.example.data.entity.datasource.UseWriteDataSource)")
    public Object forceWriteDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        DataSourceRoutingContext.push(DataSourceRoute.WRITE);
        try {
            return joinPoint.proceed();
        } finally {
            DataSourceRoutingContext.pop();
        }
    }
}
