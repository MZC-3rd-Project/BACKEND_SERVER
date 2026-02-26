package com.example.analyticsdashboard.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "analytics_agg_seller_kpi_daily",
        indexes = {
                @Index(
                        name = "uk_analytics_agg_seller_kpi_daily_business_seller",
                        columnList = "business_date,seller_id",
                        unique = true
                ),
                @Index(name = "idx_analytics_agg_seller_kpi_daily_seller", columnList = "seller_id")
        }
)
public class AnalyticsAggSellerKpiDaily extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "order_count", nullable = false)
    private Long orderCount;

    @Column(name = "cancel_count", nullable = false)
    private Long cancelCount;

    @Column(name = "refund_count", nullable = false)
    private Long refundCount;

    @Column(name = "gross_sales", nullable = false)
    private Long grossSales;

    @Column(name = "net_sales", nullable = false)
    private Long netSales;

    @Column(name = "search_count", nullable = false)
    private Long searchCount;

    @Column(name = "click_count", nullable = false)
    private Long clickCount;

    @Column(name = "ctr", nullable = false)
    private Double ctr;

    @Builder
    private AnalyticsAggSellerKpiDaily(
            LocalDate businessDate,
            Long sellerId,
            Long orderCount,
            Long cancelCount,
            Long refundCount,
            Long grossSales,
            Long netSales,
            Long searchCount,
            Long clickCount,
            Double ctr
    ) {
        this.businessDate = businessDate;
        this.sellerId = sellerId;
        this.orderCount = orderCount;
        this.cancelCount = cancelCount;
        this.refundCount = refundCount;
        this.grossSales = grossSales;
        this.netSales = netSales;
        this.searchCount = searchCount;
        this.clickCount = clickCount;
        this.ctr = ctr;
    }
}
