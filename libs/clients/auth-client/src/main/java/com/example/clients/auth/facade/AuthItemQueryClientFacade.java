package com.example.clients.auth.facade;

import com.fasterxml.jackson.databind.JsonNode;

public interface AuthItemQueryClientFacade {

    JsonNode findItem(Long itemId);


    /**
     * Returns a readable URL for one media id.
     *
     * Response example:
     * {
     *   "success": true,
     *   "data": {
     *     "userId": 100,
     *     "email": "user@example.com",
     *     "nickname": "홍길동",
     *     "phoneNumber": "010-1234-5678",
     *     // 배송지 최대 3개 등록 가능
     *     "deliveryAddresses": [
     *       {
     *         "deliveryName": "집", -- 배송지 집, 회사, 등등 배송지 이름
     *         "zipcode": "06234",
     *         "sido": "서울",
     *         "sigungu": "강남구",
     *         "roadName": "테헤란로",
     *         "buildingNumber": "123",
     *         "buildingName": "삼성빌딩",
     *         "detailAddress": "101호",
     *         "sortOrder": 0,
     *       }
     *     ]
     *   }
     * }
     * */
    JsonNode findProfileInfo(Long userId);
}
