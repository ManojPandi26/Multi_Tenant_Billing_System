package com.mtbs.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentResponse {

    private boolean valid;
    private String razorpayOrderId;
    private String razorpayPaymentId;
}
