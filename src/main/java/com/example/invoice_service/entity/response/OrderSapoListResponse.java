package com.example.invoice_service.entity.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSapoListResponse(
        @JsonProperty("orders")
        List<OrderSapoResponse> orders
) {}