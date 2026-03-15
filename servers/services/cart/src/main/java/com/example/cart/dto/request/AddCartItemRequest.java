package com.example.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AddCartItemRequest {

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

    private Boolean selected;
}
