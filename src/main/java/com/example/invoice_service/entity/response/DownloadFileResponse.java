package com.example.invoice_service.entity.response;


public record DownloadFileResponse(
        String TransactionID,
        String Data,
        String ErrorCode) {
}
