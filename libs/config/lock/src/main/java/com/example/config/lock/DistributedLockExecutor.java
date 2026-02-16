package com.example.config.lock;

import java.util.concurrent.TimeUnit;

/**
 * Distributed lock implementation contract.
 * Core lock module depends on this interface only.
 */
public interface DistributedLockExecutor {

    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException;

    boolean isHeldByCurrentThread(String lockKey);

    void unlock(String lockKey);
}
