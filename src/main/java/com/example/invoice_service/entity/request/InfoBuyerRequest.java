package com.example.invoice_service.entity.request;

import lombok.Data;

@Data
public class InfoBuyerRequest {
    String id;
    String fullNameBuyer;
    String emailBuyer;
    String addressBuyer;
    String numberPhoneBuyer;
}
