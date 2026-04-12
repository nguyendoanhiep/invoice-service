package com.example.invoice_service.entity.response;

public record PublishInvoiceResponse(
        boolean success,
        String errorCode,
        String descriptionErrorCode,
        String publishInvoiceResult
) {}
