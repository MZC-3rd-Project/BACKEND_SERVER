package com.example.store.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.store.dto.response.internal.StoreSnapshotResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "store internal query api", description = "store projection 내부 조회")
public interface StoreInternalQueryApi {

    @GetMapping("/{storeId}/snapshot")
    ApiResponse<StoreSnapshotResponse> getStoreSnapshot(@PathVariable Long storeId);

    @GetMapping("/users/{userId}/ids")
    ApiResponse<List<Long>> getActiveStoreIdsByUserId(@PathVariable Long userId);
}
