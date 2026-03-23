package com.example.order.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InternalOrderReviewEligibilityResponse {

    private boolean eligible;
}
