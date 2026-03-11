import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * SSE fan-out 측정용 프로브.
 *
 * 사용 예시:
 * java -cp /tmp/hotdeal_probe_classes SseFanoutProbe \
 *   --bases http://127.0.0.1:18089,http://127.0.0.1:18090,http://127.0.0.1:18091 \
 *   --users 2000 \
 *   --hotdeal-id 990001 \
 *   --immediate-ms 1000 \
 *   --timeout-ms 12000 \
 *   --seed 20260302 \
 *   --output /tmp/hotdeal_sse_probe_before.json
 */
public class SseFanoutProbe {

    private static final String EVENT_CONNECTED = "queue-connected";
    private static final String EVENT_POSITION = "queue-position";
    private static final String EVENT_ADMITTED = "queue-admitted";

    private static final long CONNECT_WAIT_MS = 30000L;

    private record Config(
            List<String> bases,
            int users,
            long hotDealId,
            long immediateMs,
            long timeoutMs,
            long seed,
            String output,
            String trigger,
            String redisHost,
            int redisPort,
            List<String> topics
    ) {
    }

    private static final class UserCase {
        final long userId;
        final int sseIndex;
        final int enterIndex; // trigger source index

        volatile long connectedAt = -1L;
        volatile long enterAt = -1L; // trigger sent time
        volatile long firstQueueEventAt = -1L;
        volatile int enterStatus = -1; // 200 means trigger send success
        volatile String streamError = null;
        volatile String enterError = null;

        UserCase(long userId, int sseIndex, int enterIndex) {
            this.userId = userId;
            this.sseIndex = sseIndex;
            this.enterIndex = enterIndex;
        }
    }

    public static void main(String[] args) throws Exception {
        Config config = parseArgs(args);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        List<UserCase> cases = allocateCases(config.users(), config.bases().size(), config.seed());

        List<Future<?>> streamFutures = new ArrayList<>(config.users());
        List<Future<?>> enterFutures = new ArrayList<>(config.users());

        long startedAt = System.currentTimeMillis();
        System.out.printf(Locale.ROOT,
                "[INFO] probe start users=%d bases=%s hotDealId=%d immediateMs=%d timeoutMs=%d%n",
                config.users(), config.bases(), config.hotDealId(), config.immediateMs(), config.timeoutMs());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (UserCase userCase : cases) {
                String base = config.bases().get(userCase.sseIndex);
                streamFutures.add(executor.submit(() -> streamWorker(client, base, config.hotDealId(), config.timeoutMs(), userCase)));
            }

            waitForConnections(cases, CONNECT_WAIT_MS);

            if ("queue-enter".equals(config.trigger)) {
                for (UserCase userCase : cases) {
                    String base = config.bases().get(userCase.enterIndex);
                    enterFutures.add(executor.submit(() -> enterWorker(client, base, config.hotDealId(), userCase)));
                }
                for (Future<?> future : enterFutures) {
                    future.get();
                }
            } else if ("redis-publish".equals(config.trigger)) {
                redisPublishBatch(config, cases);
            } else {
                throw new IllegalArgumentException("unsupported trigger: " + config.trigger);
            }

            long waitUntil = System.currentTimeMillis() + config.timeoutMs() + 1000L;
            while (System.currentTimeMillis() < waitUntil) {
                long completed = countWithQueueEvent(cases);
                if (completed == config.users()) {
                    break;
                }
                Thread.sleep(100L);
            }

            for (Future<?> streamFuture : streamFutures) {
                streamFuture.cancel(true);
            }
        }

        long finishedAt = System.currentTimeMillis();

        Metrics metrics = Metrics.from(cases, config);
        String json = metrics.toJson(config, startedAt, finishedAt);
        java.nio.file.Files.writeString(java.nio.file.Path.of(config.output()), json, StandardCharsets.UTF_8);

        System.out.printf(Locale.ROOT,
                "[INFO] immediateSuccessRate=%.4f eventualSuccessRate=%.4f p95=%dms notDelivered=%d output=%s%n",
                metrics.immediateSuccessRatePercent,
                metrics.eventualSuccessRatePercent,
                metrics.convergenceP95Ms,
                metrics.notDeliveredCount,
                config.output());
    }

    private static void streamWorker(HttpClient client, String base, long hotDealId, long timeoutMs, UserCase userCase) {
        String url = base + "/api/v1/hot-deals/" + hotDealId + "/queue/stream";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "text/event-stream")
                .header("X-User-Id", String.valueOf(userCase.userId))
                .GET()
                .build();

        long deadline = System.currentTimeMillis() + timeoutMs + 15000L;

        try {
            HttpResponse<java.io.InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                userCase.streamError = "stream status " + response.statusCode();
                return;
            }

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String eventName = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    if (System.currentTimeMillis() > deadline) {
                        return;
                    }

                    if (line.startsWith("event:")) {
                        eventName = line.substring("event:".length()).trim();
                    } else if (line.isEmpty()) {
                        if (EVENT_CONNECTED.equals(eventName) && userCase.connectedAt < 0L) {
                            userCase.connectedAt = System.currentTimeMillis();
                        }
                        if ((EVENT_POSITION.equals(eventName) || EVENT_ADMITTED.equals(eventName))
                                && userCase.firstQueueEventAt < 0L) {
                            userCase.firstQueueEventAt = System.currentTimeMillis();
                            return;
                        }
                        eventName = null;
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            userCase.streamError = e.getClass().getSimpleName();
        }
    }

    private static void enterWorker(HttpClient client, String base, long hotDealId, UserCase userCase) {
        String url = base + "/api/v1/hot-deals/" + hotDealId + "/queue/enter";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("X-User-Id", String.valueOf(userCase.userId))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        userCase.enterAt = System.currentTimeMillis();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            userCase.enterStatus = response.statusCode();
            if (response.statusCode() != 200) {
                userCase.enterError = "enter status " + response.statusCode();
            }
        } catch (IOException | InterruptedException e) {
            userCase.enterError = e.getClass().getSimpleName();
        }
    }

    private static void waitForConnections(List<UserCase> cases, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long connected = cases.stream().filter(c -> c.connectedAt > 0L).count();
            if (connected == cases.size()) {
                break;
            }
            Thread.sleep(100L);
        }
    }

    private static long countWithQueueEvent(List<UserCase> cases) {
        return cases.stream().filter(c -> c.firstQueueEventAt > 0L).count();
    }

    private static List<UserCase> allocateCases(int users, int baseCount, long seed) {
        Random random = new Random(seed);
        List<UserCase> result = new ArrayList<>(users);
        for (int i = 0; i < users; i++) {
            int sseIndex = random.nextInt(baseCount);
            int enterIndex = random.nextInt(baseCount);
            result.add(new UserCase(i + 1L, sseIndex, enterIndex));
        }
        return result;
    }

    private static Config parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                if (i + 1 < args.length) {
                    map.put(key, args[++i]);
                }
            }
        }

        String basesRaw = required(map, "bases");
        List<String> bases = Arrays.stream(basesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (bases.size() < 2) {
            throw new IllegalArgumentException("bases must have at least 2 endpoints");
        }

        int users = Integer.parseInt(required(map, "users"));
        long hotDealId = Long.parseLong(required(map, "hotdeal-id"));
        long immediateMs = Long.parseLong(required(map, "immediate-ms"));
        long timeoutMs = Long.parseLong(required(map, "timeout-ms"));
        long seed = Long.parseLong(map.getOrDefault("seed", "20260302"));
        String output = required(map, "output");
        String trigger = map.getOrDefault("trigger", "queue-enter").trim();
        String redisHost = map.getOrDefault("redis-host", "127.0.0.1").trim();
        int redisPort = Integer.parseInt(map.getOrDefault("redis-port", "6379").trim());
        String topicsRaw = map.getOrDefault("topics", "hotdeal-queue-sse-events");
        List<String> topics = Arrays.stream(topicsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("topics must not be empty");
        }
        if (topics.size() != 1 && topics.size() != bases.size()) {
            throw new IllegalArgumentException("topics size must be 1 or same as bases size");
        }

        return new Config(bases, users, hotDealId, immediateMs, timeoutMs, seed, output,
                trigger, redisHost, redisPort, topics);
    }

    private static String required(Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing --" + key);
        }
        return value;
    }

    private static final class Metrics {
        final int totalUsers;
        final int enterSuccessUsers;
        final int crossInstanceUsers;
        final int connectedUsers;
        final int immediateSuccessCount;
        final double immediateSuccessRatePercent;
        final int eventualSuccessCount;
        final double eventualSuccessRatePercent;
        final int notDeliveredCount;
        final long convergenceP50Ms;
        final long convergenceP95Ms;
        final long convergenceMaxMs;

        Metrics(int totalUsers,
                int enterSuccessUsers,
                int crossInstanceUsers,
                int connectedUsers,
                int immediateSuccessCount,
                double immediateSuccessRatePercent,
                int eventualSuccessCount,
                double eventualSuccessRatePercent,
                int notDeliveredCount,
                long convergenceP50Ms,
                long convergenceP95Ms,
                long convergenceMaxMs) {
            this.totalUsers = totalUsers;
            this.enterSuccessUsers = enterSuccessUsers;
            this.crossInstanceUsers = crossInstanceUsers;
            this.connectedUsers = connectedUsers;
            this.immediateSuccessCount = immediateSuccessCount;
            this.immediateSuccessRatePercent = immediateSuccessRatePercent;
            this.eventualSuccessCount = eventualSuccessCount;
            this.eventualSuccessRatePercent = eventualSuccessRatePercent;
            this.notDeliveredCount = notDeliveredCount;
            this.convergenceP50Ms = convergenceP50Ms;
            this.convergenceP95Ms = convergenceP95Ms;
            this.convergenceMaxMs = convergenceMaxMs;
        }

        static Metrics from(List<UserCase> cases, Config config) {
            int total = cases.size();
            int enterSuccess = 0;
            int crossInstance = 0;
            int connected = 0;
            int immediate = 0;
            int eventual = 0;
            List<Long> latencies = new ArrayList<>();

            for (UserCase c : cases) {
                if (c.sseIndex != c.enterIndex) {
                    crossInstance++;
                }
                if (c.connectedAt > 0L) {
                    connected++;
                }
                if (c.enterStatus == 200) {
                    enterSuccess++;
                }
                if (c.enterAt > 0L && c.firstQueueEventAt > 0L) {
                    long latency = c.firstQueueEventAt - c.enterAt;
                    if (latency <= config.timeoutMs()) {
                        eventual++;
                        latencies.add(latency);
                    }
                    if (latency <= config.immediateMs()) {
                        immediate++;
                    }
                }
            }

            int denominator = Math.max(enterSuccess, 1);
            double immediateRate = immediate * 100.0d / denominator;
            double eventualRate = eventual * 100.0d / denominator;
            int notDelivered = enterSuccess - eventual;

            long p50 = percentile(latencies, 50);
            long p95 = percentile(latencies, 95);
            long max = latencies.stream().max(Comparator.naturalOrder()).orElse(-1L);

            return new Metrics(total, enterSuccess, crossInstance, connected,
                    immediate, immediateRate, eventual, eventualRate, notDelivered, p50, p95, max);
        }

        private static long percentile(List<Long> data, int p) {
            if (data.isEmpty()) {
                return -1L;
            }
            List<Long> sorted = new ArrayList<>(data);
            sorted.sort(Long::compareTo);
            int index = (int) Math.ceil((p / 100.0d) * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }

        String toJson(Config config, long startedAt, long finishedAt) {
            return """
                    {
                      "users": %d,
                      "hotDealId": %d,
                      "bases": [%s],
                      "seed": %d,
                      "immediateWindowMs": %d,
                      "convergenceTimeoutMs": %d,
                      "startedAtEpochMs": %d,
                      "finishedAtEpochMs": %d,
                      "totalUsers": %d,
                      "enterSuccessUsers": %d,
                      "crossInstanceUsers": %d,
                      "connectedUsers": %d,
                      "immediateSuccessCount": %d,
                      "immediateSuccessRatePercent": %.4f,
                      "eventualSuccessCount": %d,
                      "eventualSuccessRatePercent": %.4f,
                      "notDeliveredCount": %d,
                      "convergenceLatencyMs": {
                        "p50": %d,
                        "p95": %d,
                        "max": %d
                      }
                    }
                    """.formatted(
                    config.users(),
                    config.hotDealId(),
                    formatBases(config.bases()),
                    config.seed(),
                    config.immediateMs(),
                    config.timeoutMs(),
                    startedAt,
                    finishedAt,
                    totalUsers,
                    enterSuccessUsers,
                    crossInstanceUsers,
                    connectedUsers,
                    immediateSuccessCount,
                    immediateSuccessRatePercent,
                    eventualSuccessCount,
                    eventualSuccessRatePercent,
                    notDeliveredCount,
                    convergenceP50Ms,
                    convergenceP95Ms,
                    convergenceMaxMs
            );
        }

        private static String formatBases(List<String> bases) {
            return bases.stream()
                    .map(base -> "\"" + base + "\"")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        }
    }
    private static void redisPublishBatch(Config config, List<UserCase> cases) {
        try (RedisPublisher publisher = new RedisPublisher(config.redisHost(), config.redisPort())) {
            for (UserCase userCase : cases) {
                String topic = config.topics().size() == 1
                        ? config.topics().getFirst()
                        : config.topics().get(userCase.enterIndex);
                String payload = "{\"hotDealId\":" + config.hotDealId()
                        + ",\"userId\":" + userCase.userId
                        + ",\"position\":1,\"canPurchase\":false}";
                userCase.enterAt = System.currentTimeMillis();
                long receivers = publisher.publish(topic, payload);
                if (receivers >= 0L) {
                    userCase.enterStatus = 200;
                } else {
                    userCase.enterError = "publish failed";
                }
            }
        } catch (Exception e) {
            for (UserCase userCase : cases) {
                if (userCase.enterStatus < 0) {
                    userCase.enterError = e.getClass().getSimpleName();
                }
            }
        }
    }

    private static final class RedisPublisher implements AutoCloseable {
        private final Socket socket;
        private final BufferedInputStream input;
        private final OutputStream output;

        RedisPublisher(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.socket.setSoTimeout(5000);
            this.input = new BufferedInputStream(this.socket.getInputStream());
            this.output = this.socket.getOutputStream();
        }

        long publish(String channel, String payload) throws IOException {
            byte[] channelBytes = channel.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            String header = "*3\r\n$7\r\nPUBLISH\r\n$" + channelBytes.length + "\r\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));
            output.write(channelBytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            String payloadHeader = "$" + payloadBytes.length + "\r\n";
            output.write(payloadHeader.getBytes(StandardCharsets.UTF_8));
            output.write(payloadBytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            return readIntegerReply();
        }

        private long readIntegerReply() throws IOException {
            int prefix = input.read();
            if (prefix != ':') {
                throw new IOException("unexpected redis reply prefix: " + (char) prefix);
            }
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = input.read()) != -1) {
                if (b == '\r') {
                    int next = input.read();
                    if (next == '\n') {
                        break;
                    }
                    throw new IOException("invalid redis CRLF sequence");
                }
                sb.append((char) b);
            }
            return Long.parseLong(sb.toString());
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
