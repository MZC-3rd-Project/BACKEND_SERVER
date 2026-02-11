package com.example.stock.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.stock.dto.request.*;
import com.example.stock.dto.response.ReservationResponse;
import com.example.stock.dto.response.StockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Stock Command", description = "재고 변경 내부 API")
public interface StockCommandApi {

    @Operation(summary = "재고 차감")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "차감 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족")
    })
    @PostMapping("/internal/v1/stock/decrease")
    ApiResponse<StockResponse> decreaseStock(@RequestBody StockDecreaseRequest request);

    @Operation(summary = "재고 증가")
    @PostMapping("/internal/v1/stock/increase")
    ApiResponse<StockResponse> increaseStock(@RequestBody StockIncreaseRequest request);

    @Operation(summary = "재고 예약 (TCC Try)")
    @PostMapping("/internal/v1/stock/reserve")
    ApiResponse<ReservationResponse> reserveStock(@RequestBody ReserveStockRequest request);

    @Operation(summary = "예약 확정 (TCC Confirm)")
    @PostMapping("/internal/v1/stock/confirm")
    ApiResponse<ReservationResponse> confirmReservation(@RequestBody ConfirmReservationRequest request);

    @Operation(summary = "예약 취소 (TCC Cancel)")
    @PostMapping("/internal/v1/stock/cancel")
    ApiResponse<ReservationResponse> cancelReservation(@RequestBody CancelReservationRequest request);

    @Operation(summary = "재고 초기화")
    @PostMapping("/internal/v1/stock/initialize")
    ApiResponse<StockResponse> initializeStock(@RequestBody InitializeStockRequest request);
}
