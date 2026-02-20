package com.example.chat.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.chat.dto.command.request.CreateInquiryRoomRequest;
import com.example.chat.dto.command.request.ChatReadUpdateRequest;
import com.example.chat.dto.command.response.ChatRoomCreateResponse;
import com.example.chat.dto.command.response.ChatReadUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Chat Room Command", description = "채팅방 생성 API")
public interface ChatRoomCommandApi {

    @Operation(summary = "상품 문의 1:1 방 생성/조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "외부 서비스 오류")
    })
    @PostMapping("/rooms/inquiries")
    ApiResponse<ChatRoomCreateResponse> createInquiryRoom(
            @Valid @RequestBody CreateInquiryRoomRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long buyerId
    );

    @Operation(summary = "채팅방 읽음 포인터 갱신")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @PostMapping("/rooms/{roomId}/read")
    ApiResponse<ChatReadUpdateResponse> updateReadPointer(
            @PathVariable Long roomId,
            @Valid @RequestBody ChatReadUpdateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId
    );
}
