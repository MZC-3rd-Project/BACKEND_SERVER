package com.example.stock.controller.command;

import com.example.api.response.ApiResponse;
import com.example.stock.controller.api.command.StockCommandApi;
import com.example.stock.dto.request.*;
import com.example.stock.dto.response.ReservationResponse;
import com.example.stock.dto.response.StockResponse;
import com.example.stock.service.command.StockCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StockCommandController implements StockCommandApi {

    private final StockCommandService stockCommandService;

    @Override
    public ApiResponse<StockResponse> decreaseStock(@Valid @RequestBody StockDecreaseRequest request) {
        return ApiResponse.success(stockCommandService.decreaseStock(request));
    }

    @Override
    public ApiResponse<StockResponse> increaseStock(@Valid @RequestBody StockIncreaseRequest request) {
        return ApiResponse.success(stockCommandService.increaseStock(request));
    }

    @Override
    public ApiResponse<ReservationResponse> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        return ApiResponse.success(stockCommandService.reserveStock(request));
    }

    @Override
    public ApiResponse<ReservationResponse> confirmReservation(@Valid @RequestBody ConfirmReservationRequest request) {
        return ApiResponse.success(stockCommandService.confirmReservation(request));
    }

    @Override
    public ApiResponse<ReservationResponse> cancelReservation(@Valid @RequestBody CancelReservationRequest request) {
        return ApiResponse.success(stockCommandService.cancelReservation(request.getReservationId()));
    }

    @Override
    public ApiResponse<StockResponse> initializeStock(@Valid @RequestBody InitializeStockRequest request) {
        return ApiResponse.success(stockCommandService.initializeStock(request));
    }
}
