package com.example.cart.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangeCartSelectionRequest {

    @Valid
    @NotEmpty
    private List<LineItemSelection> lineItems;

    @Getter
    @NoArgsConstructor
    public static class LineItemSelection {

        @NotNull
        private Long itemId;

        @NotNull
        private Long referenceId;

        @NotBlank
        private String channelType;

        private Long channelRefId;

        @NotNull
        private Boolean selected;
    }
}
