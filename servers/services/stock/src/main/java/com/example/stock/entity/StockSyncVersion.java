package com.example.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_sync_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockSyncVersion {

    @Id
    private Long itemId;

    @Column(nullable = false)
    private long version;

    public static StockSyncVersion initialize(Long itemId) {
        StockSyncVersion syncVersion = new StockSyncVersion();
        syncVersion.itemId = itemId;
        syncVersion.version = 0L;
        return syncVersion;
    }

    public long incrementAndGet() {
        this.version += 1L;
        return this.version;
    }
}
