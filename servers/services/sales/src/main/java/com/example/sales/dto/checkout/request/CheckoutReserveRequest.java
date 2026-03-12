package com.example.sales.dto.checkout.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CheckoutReserveRequest {

    @Valid
    @NotEmpty
    private List<LineItem> lineItems;

    @NotBlank
    private String idempotencyKey;

    @Getter
    @NoArgsConstructor
    public static class LineItem {

        @NotNull
        private Long itemId;

        @NotBlank
        private String channelType;

        private Long channelRefId;

        @NotBlank
        private String stockItemType;

        @NotNull
        private Long referenceId;

        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
