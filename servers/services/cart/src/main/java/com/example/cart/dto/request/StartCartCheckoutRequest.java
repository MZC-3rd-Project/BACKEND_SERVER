package com.example.cart.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class StartCartCheckoutRequest {

    @NotBlank
    private String idempotencyKey;

    @Valid
    @NotEmpty
    private List<LineItem> lineItems;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineItem {

        @NotNull
        @Positive
        private Long itemId;

        @NotNull
        @Positive
        private Long referenceId;

        @NotBlank
        private String channelType;

        private Long channelRefId;

        @NotBlank
        private String stockItemType;

        @NotNull
        @Positive
        private Integer quantity;
    }
}
