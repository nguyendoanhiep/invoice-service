package com.example.invoice_service.entity.response;

import java.math.BigDecimal;

public interface OrderReportDetails {

    String getName();

    Integer getMonth();

    String getSource();

    Long getTotalOrders();

    BigDecimal getTotalAmount();

    BigDecimal getTotalOriginalAmount();

    BigDecimal getTotalVatAmount();
}
