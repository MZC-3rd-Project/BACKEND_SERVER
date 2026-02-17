package com.example.config.lock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

@AutoConfiguration
public class LockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExpressionParser lockExpressionParser() {
        return new SpelExpressionParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLockAspect distributedLockAspect(
            ObjectProvider<DistributedLockExecutor> lockExecutorProvider,
            ExpressionParser lockExpressionParser
    ) {
        return new DistributedLockAspect(lockExecutorProvider, lockExpressionParser);
    }
}
