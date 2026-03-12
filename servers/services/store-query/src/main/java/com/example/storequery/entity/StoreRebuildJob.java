package com.example.storequery.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "store_rebuild_jobs")
public class StoreRebuildJob extends BaseEntity {

    @Id
    @SnowflakeGenerated
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    private StoreRebuildJobScopeType scopeType;

    @Column(name = "scope_key", length = 100)
    private String scopeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StoreRebuildJobStatus status;

    @Builder.Default
    @Column(name = "processed_count", nullable = false)
    private Integer processedCount = 0;

    @Builder.Default
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
