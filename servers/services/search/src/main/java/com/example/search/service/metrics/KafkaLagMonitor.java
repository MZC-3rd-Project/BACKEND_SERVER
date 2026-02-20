package com.example.search.service.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaLagMonitor {

    private final SearchMetricsService searchMetricsService;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${search.metrics.kafka-lag.enabled:true}")
    private boolean kafkaLagEnabled;

    @Scheduled(fixedDelayString = "${search.metrics.kafka-lag.interval-ms:30000}")
    public void collectLag() {
        if (!kafkaLagEnabled) {
            return;
        }

        Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 1000);

        try (AdminClient adminClient = AdminClient.create(props)) {
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(consumerGroupId);
            Map<TopicPartition, OffsetAndMetadata> committed =
                    offsetsResult.partitionsToOffsetAndMetadata().get(1, TimeUnit.SECONDS);

            if (committed == null || committed.isEmpty()) {
                searchMetricsService.updateKafkaLag(0L);
                return;
            }

            Map<TopicPartition, OffsetSpec> latestOffsetRequests = new HashMap<>();
            for (TopicPartition topicPartition : committed.keySet()) {
                latestOffsetRequests.put(topicPartition, OffsetSpec.latest());
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    adminClient.listOffsets(latestOffsetRequests).all().get(1, TimeUnit.SECONDS);

            long totalLag = 0L;
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committed.entrySet()) {
                TopicPartition topicPartition = entry.getKey();
                long committedOffset = entry.getValue().offset();
                long latestOffset = latestOffsets.get(topicPartition).offset();
                totalLag += Math.max(0L, latestOffset - committedOffset);
            }
            searchMetricsService.updateKafkaLag(totalLag);
        } catch (Exception e) {
            searchMetricsService.updateKafkaLag(-1L);
            log.debug("Kafka lag metrics collection failed.", e);
        }
    }
}
