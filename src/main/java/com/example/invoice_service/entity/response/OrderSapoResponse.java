package com.example.invoice_service.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSapoResponse(

        Long id,

        @JsonProperty("created_on")
        LocalDateTime createdOn,

        @JsonProperty("modified_on")
        String modifiedOn,

        String email,

        String currency,

        String phone,

        @JsonProperty("financial_status")
        String financialStatus,

        String status,

        String gateway,

        @JsonProperty("return_status")
        String returnStatus,

        @JsonProperty("source_name")
        String sourceName,

        @JsonProperty("total_price")
        BigDecimal totalPrice,

        @JsonProperty("billing_address")
        BillingAddress billingAddress,

        @JsonProperty("line_items")
        List<LineItem> lineItems,

        @JsonProperty("user")
        User user,

        @JsonProperty("note")
        String note

) {
    public record LineItem(

            Long id,

            BigDecimal price,

            Integer quantity,

            String name,

            String sku,

            @JsonProperty("original_total")
            BigDecimal originalTotal,

            Boolean deleted

    ) {}

    public record BillingAddress(

            String address1,
            String address2,
            String city,

            @JsonProperty("first_name")
            String firstName,

            @JsonProperty("last_name")
            String lastName,

            String phone,
            String country,
            String name

    ) {}
    public record User(

            String id,
            String first_name,
            String last_name

    ) {}
}
