package com.example.product.service.query.detail;

import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.performance.CastMember;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;

import java.util.List;

public record PerformanceItemDetailView(
        Item item,
        ItemCategoryDetailView categoryDetail,
        Performance performance,
        List<SeatGrade> seatGrades,
        List<CastMember> castMembers,
        ItemContentSnapshot contentSnapshot,
        List<ItemImage> images
) {
    public PerformanceItemDetailView {
        seatGrades = seatGrades == null ? List.of() : List.copyOf(seatGrades);
        castMembers = castMembers == null ? List.of() : List.copyOf(castMembers);
        contentSnapshot = contentSnapshot == null ? ItemContentSnapshot.empty() : contentSnapshot;
        images = images == null ? List.of() : List.copyOf(images);
    }
}
