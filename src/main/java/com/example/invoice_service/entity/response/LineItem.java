package com.example.invoice_service.entity.response;

import lombok.Builder;

import java.util.List;
@Builder
public record LineItem(
        Long id,
        String name,
        String sku,
        Integer quantity,
        String total,
        Integer price,
        List<MetaData> meta_data
) {}