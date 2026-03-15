package com.example.hotdeal.controller.api;

import com.example.api.response.ApiResponse;
import com.example.hotdeal.dto.HotDealDetailResponse;
import com.example.hotdeal.dto.query.response.HotDealDetailQueryResponse;
import com.example.hotdeal.dto.query.response.HotDealListQueryResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HotDealApiContractTest {

    @Test
    void queryApi_usesQuerySpecificDtos() throws NoSuchMethodException {
        Method getDetail = HotDealQueryApi.class.getMethod("getDetail", Long.class);
        Method getActiveDeals = HotDealQueryApi.class.getMethod("getActiveDeals", Long.class, int.class);

        assertThat(apiResponseTypeArgument(getDetail)).isEqualTo(HotDealDetailQueryResponse.class);
        assertThat(firstListElementType(apiResponseTypeArgument(getActiveDeals))).isEqualTo(HotDealListQueryResponse.class);
    }

    @Test
    void commandApi_keepsCommandDetailDto() throws NoSuchMethodException {
        Method createHotDeal = HotDealCommandApi.class.getMethod(
                "createHotDeal",
                com.example.hotdeal.dto.CreateHotDealRequest.class,
                Long.class
        );

        assertThat(apiResponseTypeArgument(createHotDeal)).isEqualTo(HotDealDetailResponse.class);
    }

    private Type apiResponseTypeArgument(Method method) {
        ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
        assertThat(returnType.getRawType()).isEqualTo(ApiResponse.class);
        return returnType.getActualTypeArguments()[0];
    }

    private Type firstListElementType(Type outerType) {
        ParameterizedType listType = (ParameterizedType) outerType;
        assertThat(listType.getRawType()).isEqualTo(List.class);
        return listType.getActualTypeArguments()[0];
    }
}
