package com.example.invoice_service.entity.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PublishInvoiceBody(
        @JsonProperty("SignType")
        int signType,

        @JsonProperty("InvoiceData")
        List<InvoiceData> invoiceData,

        @JsonProperty("PublishInvoiceData")
        Object publishInvoiceData // null lúc tạo, có thể bỏ
) {}