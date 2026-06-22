package com.mtbs.shared.enums.billing;

import java.util.Map;

public enum PaymentMethod {
    CARD,
    UPI,
    NETBANKING,
    BANK_TRANSFER,
    CASH,
    CHEQUE;

    private static final Map<String, PaymentMethod> RAZORPAY_MAP = Map.of(
            "card", CARD,
            "upi", UPI,
            "netbanking", NETBANKING,
            "wallet", BANK_TRANSFER,
            "bank_transfer", BANK_TRANSFER
    );

    public static PaymentMethod fromRazorpayMethod(String method) {
        if (method == null) return BANK_TRANSFER;
        return RAZORPAY_MAP.getOrDefault(method.toLowerCase(), BANK_TRANSFER);
    }
}
