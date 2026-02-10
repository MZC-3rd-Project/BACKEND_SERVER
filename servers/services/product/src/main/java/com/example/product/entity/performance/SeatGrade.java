package com.example.product.entity.performance;

import com.example.core.exception.BusinessException;
import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.product.exception.ProductErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "seat_grades", indexes = {
        @Index(name = "idx_seat_grades_performance_id", columnList = "performance_id")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatGrade extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "grade_name", nullable = false, length = 50)
    private String gradeName;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "funding_quantity", nullable = false)
    private Integer fundingQuantity;

    public static SeatGrade create(Long performanceId, String gradeName, Long price,
                                   int totalQuantity, int fundingQuantity) {
        validateQuantity(totalQuantity, fundingQuantity);
        SeatGrade sg = new SeatGrade();
        sg.performanceId = performanceId;
        sg.gradeName = gradeName;
        sg.price = price;
        sg.totalQuantity = totalQuantity;
        sg.fundingQuantity = fundingQuantity;
        return sg;
    }

    public void update(String gradeName, Long price, int totalQuantity, int fundingQuantity) {
        validateQuantity(totalQuantity, fundingQuantity);
        this.gradeName = gradeName;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.fundingQuantity = fundingQuantity;
    }

    private static void validateQuantity(int totalQuantity, int fundingQuantity) {
        if (fundingQuantity > totalQuantity) {
            throw new BusinessException(ProductErrorCode.INVALID_FUNDING_QUANTITY);
        }
    }
}
