package com.example.funding.service.query;

import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.funding.dto.participation.response.ParticipationResponse;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.repository.FundingParticipationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationQueryService {

    private final FundingParticipationRepository participationRepository;

    public CursorResponse<ParticipationResponse> findByUserId(Long userId, String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<FundingParticipation> participations =
                participationRepository.findByUserIdWithCursor(userId, cursorId, pageable);

        boolean hasNext = participations.size() > size;
        List<FundingParticipation> pageItems = hasNext ? participations.subList(0, size) : participations;

        List<ParticipationResponse> content = pageItems.stream()
                .map(ParticipationResponse::from)
                .toList();

        String nextCursor = hasNext
                ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }

    public List<ParticipationResponse> findByCampaignId(Long campaignId) {
        return participationRepository.findByCampaignId(campaignId).stream()
                .map(ParticipationResponse::from)
                .toList();
    }
}
