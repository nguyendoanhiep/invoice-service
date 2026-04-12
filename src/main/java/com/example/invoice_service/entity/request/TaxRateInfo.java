package com.example.invoice_service.entity.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record TaxRateInfo(
        @JsonProperty("VATRateName")
        String vatRateName,

        @JsonProperty("AmountWithoutVATOC")
        double amountWithoutVATOC,

        @JsonProperty("VATAmountOC")
        double vatAmountOC
) {}