package com.example.invoice_service.entity.response;

public record PublishInvoiceItemResponse(
        String RefID,
        String TransactionID,
        String InvTemplateNo,
        String InvSeries,
        String InvNo,
        String InvCode,
        String InvDate,
        String ErrorCode,
        String DescriptionErrorCode,
        String ErrorData,
        String CustomData
) {}
