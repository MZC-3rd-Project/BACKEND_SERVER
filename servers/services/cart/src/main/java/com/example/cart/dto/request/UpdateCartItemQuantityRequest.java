package com.example.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateCartItemQuantityRequest {

    @NotNull
    private Long itemId;

    @NotNull
    private Long referenceId;

    @NotBlank
    private String channelType;

    private Long channelRefId;

    @NotNull
    @Min(1)
    private Integer quantity;
}
