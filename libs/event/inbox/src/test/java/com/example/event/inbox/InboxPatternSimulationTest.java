package com.example.event.inbox;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InboxPatternSimulationTest {

    private static final int EVENT_COUNT = 800;

    @Test
    void comparePollOnlyAndPushFallbackAcrossScenarios() throws Exception {
        List<Integer> scheduleMs = buildSchedule(EVENT_COUNT, 1, 6, 42L);

        ScenarioResult poll200NoFailure = runScenario("poll-only/200ms/no-failure", false, 200, 0, scheduleMs);
        ScenarioResult push200NoFailure = runScenario("push+fallback/200ms/no-failure", true, 200, 0, scheduleMs);

        ScenarioResult poll1000NoFailure = runScenario("poll-only/1000ms/no-failure", false, 1000, 0, scheduleMs);
        ScenarioResult push1000NoFailure = runScenario("push+fallback/1000ms/no-failure", true, 1000, 0, scheduleMs);

        ScenarioResult poll200FailOnce = runScenario("poll-only/200ms/fail-once", false, 200, 1, scheduleMs);
        ScenarioResult push200FailOnce = runScenario("push+fallback/200ms/fail-once", true, 200, 1, scheduleMs);

        print(poll200NoFailure);
        print(push200NoFailure);
        print(poll1000NoFailure);
        print(push1000NoFailure);
        print(poll200FailOnce);
        print(push200FailOnce);

        assertThat(push200NoFailure.p95Ms()).isLessThan(poll200NoFailure.p95Ms());
        assertThat(push1000NoFailure.p95Ms()).isLessThan(poll1000NoFailure.p95Ms());

        assertThat(poll200FailOnce.successCount()).isEqualTo(EVENT_COUNT);
        assertThat(push200FailOnce.successCount()).isEqualTo(EVENT_COUNT);
        assertThat(poll200FailOnce.retryCount()).isGreaterThanOrEqualTo(EVENT_COUNT);
        assertThat(push200FailOnce.retryCount()).isGreaterThanOrEqualTo(EVENT_COUNT);
    }

    private ScenarioResult runScenario(
            String name,
            boolean pushFirst,
            int pollMs,
            int forcedFailuresPerEvent,
            List<Integer> scheduleMs
    ) throws Exception {
        Worker worker = new Worker(pushFirst, pollMs, forcedFailuresPerEvent);
        worker.start();

        long startedAt = System.nanoTime();
        for (int i = 0; i < scheduleMs.size(); i++) {
            TimeUnit.MILLISECONDS.sleep(scheduleMs.get(i));
            worker.enqueue("evt-" + i);
        }

        boolean completed = worker.awaitSuccessCount(scheduleMs.size(), 60_000);
        assertThat(completed)
                .as("worker should process all events. scenario=%s", name)
                .isTrue();

        long endedAt = System.nanoTime();
        worker.shutdown();

        return worker.summarize(name, pollMs, scheduleMs.size(), startedAt, endedAt);
    }

    private List<Integer> buildSchedule(int count, int minDelayMs, int maxDelayMs, long seed) {
        Random random = new Random(seed);
        List<Integer> scheduleMs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            scheduleMs.add(minDelayMs + random.nextInt(maxDelayMs - minDelayMs + 1));
        }
        return scheduleMs;
    }

    private void print(ScenarioResult result) {
        System.out.printf(
                Locale.US,
                "scenario=%s pollMs=%d events=%d duration=%.2fs avg=%.2fms p95=%.2fms p99=%.2fms wakeups=%d emptyWakeups=%d retries=%d success=%d%n",
                result.name(),
                result.pollMs(),
                result.events(),
                result.durationSec(),
                result.avgMs(),
                result.p95Ms(),
                result.p99Ms(),
                result.wakeups(),
                result.emptyWakeups(),
                result.retryCount(),
                result.successCount()
        );
    }

    record ScenarioResult(
            String name,
            int pollMs,
            int events,
            double durationSec,
            double avgMs,
            double p95Ms,
            double p99Ms,
            long wakeups,
            long emptyWakeups,
            long retryCount,
            int successCount
    ) {
    }

    private static final class Worker {

        private final boolean pushFirst;
        private final int pollMs;
        private final int forcedFailuresPerEvent;

        private final Object monitor = new Object();
        private final PriorityQueue<EventTask> queue = new PriorityQueue<>(Comparator.comparingLong(task -> task.availableAtNanos));
        private final List<Long> latenciesNanos = new ArrayList<>();

        private Thread thread;
        private boolean running = true;
        private long wakeups;
        private long emptyWakeups;
        private long retryCount;
        private int successCount;

        private Worker(boolean pushFirst, int pollMs, int forcedFailuresPerEvent) {
            this.pushFirst = pushFirst;
            this.pollMs = pollMs;
            this.forcedFailuresPerEvent = forcedFailuresPerEvent;
        }

        void start() {
            thread = new Thread(this::runLoop, "inbox-pattern-sim-worker");
            thread.start();
        }

        void enqueue(String eventId) {
            long now = System.nanoTime();
            synchronized (monitor) {
                queue.add(new EventTask(eventId, now, now));
                if (pushFirst) {
                    monitor.notifyAll();
                }
            }
        }

        boolean awaitSuccessCount(int expectedSuccessCount, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                synchronized (monitor) {
                    if (successCount >= expectedSuccessCount) {
                        return true;
                    }
                }
                TimeUnit.MILLISECONDS.sleep(10);
            }
            synchronized (monitor) {
                return successCount >= expectedSuccessCount;
            }
        }

        void shutdown() throws InterruptedException {
            synchronized (monitor) {
                running = false;
                monitor.notifyAll();
            }
            thread.join();
        }

        ScenarioResult summarize(String name, int pollMs, int events, long startedAtNanos, long endedAtNanos) {
            List<Long> sorted;
            synchronized (monitor) {
                sorted = new ArrayList<>(latenciesNanos);
            }
            sorted.sort(Long::compareTo);

            double avgMs = sorted.stream().mapToDouble(v -> v / 1_000_000.0).average().orElse(0.0);
            double p95Ms = percentile(sorted, 95);
            double p99Ms = percentile(sorted, 99);

            return new ScenarioResult(
                    name,
                    pollMs,
                    events,
                    (endedAtNanos - startedAtNanos) / 1_000_000_000.0,
                    avgMs,
                    p95Ms,
                    p99Ms,
                    wakeups,
                    emptyWakeups,
                    retryCount,
                    successCount
            );
        }

        private double percentile(List<Long> sortedNanos, int percentile) {
            if (sortedNanos.isEmpty()) {
                return 0.0;
            }
            int index = (int) Math.ceil((percentile / 100.0) * sortedNanos.size()) - 1;
            int boundedIndex = Math.max(0, Math.min(index, sortedNanos.size() - 1));
            return sortedNanos.get(boundedIndex) / 1_000_000.0;
        }

        private void runLoop() {
            while (true) {
                long waitMillis;
                synchronized (monitor) {
                    wakeups++;
                    int processed = processDueTasks(System.nanoTime());
                    if (processed == 0) {
                        emptyWakeups++;
                    }

                    if (!running && queue.isEmpty()) {
                        return;
                    }

                    waitMillis = computeWaitMillis();
                    try {
                        monitor.wait(waitMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private int processDueTasks(long nowNanos) {
            int processed = 0;
            while (!queue.isEmpty()) {
                EventTask head = queue.peek();
                if (head.availableAtNanos > nowNanos) {
                    break;
                }

                queue.poll();
                head.attempts++;

                if (head.attempts <= forcedFailuresPerEvent) {
                    retryCount++;
                    long delayNanos = computeBackoffNanos(head.attempts);
                    head.availableAtNanos = System.nanoTime() + delayNanos;
                    queue.add(head);
                } else {
                    successCount++;
                    latenciesNanos.add(System.nanoTime() - head.enqueuedAtNanos);
                }
                processed++;
                nowNanos = System.nanoTime();
            }
            return processed;
        }

        private long computeWaitMillis() {
            long fallbackWaitMs = Math.max(1, pollMs);
            if (!pushFirst) {
                return fallbackWaitMs;
            }
            if (queue.isEmpty()) {
                return fallbackWaitMs;
            }

            long nowNanos = System.nanoTime();
            long delayNanos = queue.peek().availableAtNanos - nowNanos;
            if (delayNanos <= 0) {
                return 1;
            }

            long dueWaitMs = TimeUnit.NANOSECONDS.toMillis(delayNanos);
            if (dueWaitMs <= 0) {
                dueWaitMs = 1;
            }
            return Math.min(fallbackWaitMs, dueWaitMs);
        }

        private long computeBackoffNanos(int attempt) {
            long baseMs = 50L;
            long multiplier = 1L << Math.max(0, attempt - 1);
            long backoffMs = Math.min(baseMs * multiplier, 400L);
            return TimeUnit.MILLISECONDS.toNanos(backoffMs);
        }
    }

    private static final class EventTask {
        private final String eventId;
        private final long enqueuedAtNanos;
        private long availableAtNanos;
        private int attempts;

        private EventTask(String eventId, long enqueuedAtNanos, long availableAtNanos) {
            this.eventId = eventId;
            this.enqueuedAtNanos = enqueuedAtNanos;
            this.availableAtNanos = availableAtNanos;
            this.attempts = 0;
        }
    }
}
