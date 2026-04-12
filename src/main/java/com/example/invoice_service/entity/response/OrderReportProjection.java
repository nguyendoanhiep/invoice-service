package com.example.invoice_service.entity.response;

import java.math.BigDecimal;

public interface OrderReportProjection {
    String getSource();
    Long getTotalOrders();
    BigDecimal getTotalAmount();
    BigDecimal getTotalOriginalAmount();
    BigDecimal getTotalVatAmount();
}
