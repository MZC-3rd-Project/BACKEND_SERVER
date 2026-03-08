package com.example.store.controller.command;

import com.example.api.response.ApiResponse;
import com.example.store.controller.api.command.StoreCommandApi;
import com.example.store.dto.request.StoreCreateRequest;
import com.example.store.dto.response.StoreCreateResponse;
import com.example.store.service.command.StoreCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.shaded.com.google.protobuf.Api;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/store")
public class StoreCommandController implements StoreCommandApi {

    private final StoreCommandService storeCommandService;

    @Override
    public ApiResponse<StoreCreateResponse> createStore(Long userId, StoreCreateRequest request) {

        return ApiResponse.success(storeCommandService.create(userId,request));
    }
}
