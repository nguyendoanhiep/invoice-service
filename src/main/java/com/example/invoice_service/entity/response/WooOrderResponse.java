package com.example.invoice_service.entity.response;

import java.util.List;

public record WooOrderResponse(
        Long id,
        String status,
        String currency,
        String total,
        String date_created,
        String date_completed,
        String payment_method,
        String payment_method_title,
        Billing billing,
        List<LineItem> line_items,
        List<MetaData> meta_data
) {
    public record MetaData(
            Long id,
            String key,
            String value
    ){}
}
