package com.example.invoice_service.entity.response;

import lombok.Builder;

@Builder
public record Billing(
        String first_name,
        String last_name,
        String email,
        String phone,
        String address_1,
        String city,
        String country
) {}