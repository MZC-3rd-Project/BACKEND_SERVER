package com.example.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RemoveCartItemRequest {

    @NotNull
    private Long itemId;

    @NotNull
    private Long referenceId;

    @NotBlank
    private String channelType;

    private Long channelRefId;
}
