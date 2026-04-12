package com.example.invoice_service.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeInvoiceAuthResponse(
        boolean success,
        @JsonProperty("errorCode")
        String errorCode,
        @JsonProperty("descriptionErrorCode")
        String descriptionErrorCode,
        Object[] errors,
        String data,
        @JsonProperty("customData")
        String customData
) {}
