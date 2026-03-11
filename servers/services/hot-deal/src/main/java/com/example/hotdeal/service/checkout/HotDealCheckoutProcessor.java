package com.example.hotdeal.service.checkout;

import com.example.hotdeal.dto.HotDealPurchaseResponse;

/**
 * purchase 요청 이후의 checkout 책임을 캡슐화한다.
 * 현재는 Redis 기반 구현이지만, 이후 order 연동 시 이 구현만 교체할 수 있다.
 */
public interface HotDealCheckoutProcessor {

    HotDealPurchaseResponse checkout(HotDealCheckoutCommand command);
}
