package com.example.hotdeal.controller;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalHotDealControllerRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createHotDealRequestRequiresCoreFields() throws Exception {
        InternalHotDealController.CreateHotDealRequest request =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue("{}", InternalHotDealController.CreateHotDealRequest.class);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("itemId", "title", "originalPrice", "discountRate", "maxQuantity");
    }
}
