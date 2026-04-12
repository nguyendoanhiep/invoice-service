package com.example.invoice_service.entity.request;

public record SourceRequest(
        String name,
        boolean publishInvoice
) {}
