package com.example.sales.service.command;

import com.example.clients.order.exception.OrderClientConflictException;
import com.example.clients.order.exception.OrderClientException;
import com.example.clients.order.exception.OrderClientRetriableException;
import com.example.clients.order.exception.OrderClientTerminalException;
import com.example.clients.order.exception.OrderClientValidationException;
import com.example.sales.exception.SalesErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ImmediateCancelCheckoutSubmitFailurePolicy implements CheckoutSubmitFailurePolicy {

    @Override
    public CheckoutSubmitFailureDecision decide(OrderClientException exception) {
        if (exception instanceof OrderClientValidationException) {
            return CheckoutSubmitFailureDecision.cancelImmediately(SalesErrorCode.CHECKOUT_SUBMIT_INVALID);
        }
        if (exception instanceof OrderClientConflictException) {
            return CheckoutSubmitFailureDecision.cancelImmediately(SalesErrorCode.CHECKOUT_SUBMIT_CONFLICT);
        }
        if (exception instanceof OrderClientRetriableException) {
            return CheckoutSubmitFailureDecision.cancelImmediately(SalesErrorCode.ORDER_SERVICE_ERROR);
        }
        if (exception instanceof OrderClientTerminalException) {
            return CheckoutSubmitFailureDecision.cancelImmediately(SalesErrorCode.CHECKOUT_SUBMIT_INVALID);
        }
        return CheckoutSubmitFailureDecision.cancelImmediately(SalesErrorCode.ORDER_SERVICE_ERROR);
    }
}
