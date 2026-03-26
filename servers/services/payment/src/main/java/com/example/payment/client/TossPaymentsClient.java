package com.example.payment.client;

import com.example.payment.client.dto.TossCancelRequest;
import com.example.payment.client.dto.TossCancelResponse;
import com.example.payment.client.dto.TossConfirmRequest;
import com.example.payment.client.dto.TossConfirmResponse;
import com.example.payment.client.dto.TossPaymentResponse;

public interface TossPaymentsClient {

    TossConfirmResponse confirm(TossConfirmRequest request);

    TossCancelResponse cancel(String paymentKey, TossCancelRequest request);

    TossPaymentResponse query(String paymentKey);
}
