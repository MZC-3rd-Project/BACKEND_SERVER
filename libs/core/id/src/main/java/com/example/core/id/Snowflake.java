package com.example.core.id;

import java.time.Instant;

/**
 * Twitter Snowflake 기반 분산 ID 생성기.
 *
 * 구조 (64-bit signed long):
 *   0 | 41-bit timestamp | 5-bit datacenter | 5-bit worker | 12-bit sequence
 *
 * - timestamp : 커스텀 에포크(2024-01-01) 기준 밀리초 → ~69년 사용 가능
 * - datacenter: 0~31 (5-bit)
 * - worker    : 0~31 (5-bit)
 * - sequence  : 0~4095 (12-bit) → 밀리초당 최대 4096개
 *
 * 내부적으로 long으로 처리하고, JSON 직렬화 시 String으로 변환하여
 * JavaScript Number 정밀도 손실(2^53)을 방지한다.
 */
public class Snowflake {

    /** 커스텀 에포크: 2024-01-01T00:00:00Z */
    private static final long EPOCH = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();

    private static final int DATACENTER_ID_BITS = 5;
    private static final int WORKER_ID_BITS = 5;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);         // 31
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);           // 4095

    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;                           // 12
    private static final int DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;      // 17
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS; // 22

    private final long datacenterId;
    private final long workerId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public Snowflake(long datacenterId, long workerId) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    "datacenterId must be between 0 and " + MAX_DATACENTER_ID + ", got: " + datacenterId);
        }
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be between 0 and " + MAX_WORKER_ID + ", got: " + workerId);
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * 다음 Snowflake ID를 생성한다 (thread-safe).
     */
    public synchronized long nextId() {
        long currentTimestamp = currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                // NTP 등에 의한 소규모 클럭 역행은 대기로 처리
                currentTimestamp = waitNextMillis(lastTimestamp);
            } else {
                throw new IllegalStateException(
                        "Clock moved backwards. Refusing to generate id for " + offset + " ms");
            }
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 시퀀스 오버플로우 → 다음 밀리초까지 대기
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * Snowflake ID를 String으로 변환하여 반환한다.
     * JavaScript Number 정밀도 손실 방지용.
     */
    public String nextIdAsString() {
        return String.valueOf(nextId());
    }

    /**
     * Snowflake ID에서 생성 시각을 추출한다.
     */
    public static Instant extractTimestamp(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + EPOCH;
        return Instant.ofEpochMilli(timestamp);
    }

    /**
     * Snowflake ID에서 datacenter ID를 추출한다.
     */
    public static long extractDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * Snowflake ID에서 worker ID를 추출한다.
     */
    public static long extractWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * Snowflake ID에서 시퀀스 번호를 추출한다.
     */
    public static long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            java.util.concurrent.locks.LockSupport.parkNanos(100_000); // 100μs 대기로 CPU 낭비 방지
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
