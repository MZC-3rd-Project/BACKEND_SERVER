package com.example.hotdeal.service;

import com.example.hotdeal.dto.query.response.HotDealDetailQueryResponse;
import com.example.hotdeal.dto.query.response.HotDealListQueryResponse;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.query.HotDealDetailCache;
import com.example.hotdeal.service.query.HotDealDetailReader;
import com.example.hotdeal.service.query.HotDealQueryAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotDealQueryService {

    private final HotDealRepository hotDealRepository;
    private final HotDealDetailCache hotDealDetailCache;
    private final HotDealDetailReader hotDealDetailReader;
    private final HotDealQueryAssembler hotDealQueryAssembler;

    public List<HotDealListQueryResponse> getActiveDeals(Long cursor, int size) {
        if (cursor == null || cursor == 0) {
            cursor = Long.MAX_VALUE;
        }

        return hotDealRepository.findByStatusWithCursor(
                        HotDealStatus.ACTIVE, cursor, PageRequest.of(0, size))
                .stream()
                .map(hotDealQueryAssembler::toListResponse)
                .toList();
    }

    public HotDealDetailQueryResponse getDetail(Long hotDealId) {
        return hotDealDetailCache.get(hotDealId)
                .orElseGet(() -> {
                    HotDealDetailQueryResponse response = hotDealQueryAssembler.toDetailResponse(
                            hotDealDetailReader.read(hotDealId)
                    );
                    hotDealDetailCache.put(hotDealId, response);
                    return response;
                });
    }
}
