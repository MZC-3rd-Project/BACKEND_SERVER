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

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "analytics_agg_seller_kpi_monthly",
        indexes = {
                @Index(
                        name = "uk_analytics_agg_seller_kpi_monthly_business_store",
                        columnList = "business_month,store_id",
                        unique = true
                ),
                @Index(name = "idx_analytics_agg_seller_kpi_monthly_store", columnList = "store_id"),
                @Index(name = "idx_analytics_agg_seller_kpi_monthly_seller", columnList = "seller_id")
        }
)
public class AnalyticsAggSellerKpiMonthly extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_month", nullable = false, length = 7)
    private String businessMonth;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

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
    private AnalyticsAggSellerKpiMonthly(
            String businessMonth,
            Long storeId,
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
        this.businessMonth = businessMonth;
        this.storeId = storeId;
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
