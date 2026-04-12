package com.example.invoice_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "orders")
public class Orders {
    @Id
    private String id;
    private String status;
    private String source;
    private String systemName;
    private String currency;
    private BigDecimal total;
    private LocalDateTime dateCreated;
    private String dateCompleted;
    private String paymentMethod;
    private String fullNameBuyer;
    private String addressBuyer;
    private String emailBuyer;
    private String numberPhoneBuyer;
    @Column(length = 2000)
    private String lineItems;
    private String publishInvoiceStatus = "INIT";
    private String transactionID;
    private BigDecimal originalAmount;
    private BigDecimal vatAmount;
    private String issueDateInvoice;
    private String rootSource;
    private String staffName;
    private String note;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (fullNameBuyer == null) {
            fullNameBuyer = "";
        }
        if (emailBuyer == null) {
            emailBuyer = "";
        }
    }
}
