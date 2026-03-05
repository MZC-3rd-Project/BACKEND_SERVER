package com.example.store.controller.query;

import com.example.api.response.ApiResponse;
import com.example.store.controller.api.query.StoreQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController implements StoreQueryApi {

    @Override
    public ApiResponse<?> getMyStore(Long userId) {

        return null;
    }
}
