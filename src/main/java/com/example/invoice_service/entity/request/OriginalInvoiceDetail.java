package com.example.invoice_service.entity.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OriginalInvoiceDetail(
        @JsonProperty("ItemType")
        int itemType,

        @JsonProperty("SortOrder")
        int sortOrder,

        @JsonProperty("LineNumber")
        int lineNumber,

        @JsonProperty("ItemCode")
        String itemCode,

        @JsonProperty("ItemName")
        String itemName,

        @JsonProperty("UnitName")
        String unitName,

        @JsonProperty("Quantity")
        double quantity,

        @JsonProperty("UnitPrice")
        double unitPrice,

        @JsonProperty("DiscountRate")
        double discountRate,

        @JsonProperty("DiscountAmountOC")
        double discountAmountOC,

        @JsonProperty("Amount")
        double amount,

        @JsonProperty("AmountOC")
        double amountOC,

        @JsonProperty("AmountWithoutVAT")
        double amountWithoutVAT,

        @JsonProperty("AmountWithoutVATOC")
        double amountWithoutVATOC,

        @JsonProperty("VATRateName")
        String vatRateName,

        @JsonProperty("VATAmount")
        double vatAmount,
        @JsonProperty("VATAmountOC")
        double vatAmountOC
) {}
