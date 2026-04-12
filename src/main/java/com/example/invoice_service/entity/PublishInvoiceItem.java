package com.example.invoice_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Data
@Table(name = "publish_invoice_item")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    String RefID;
    String TransactionID;
    String InvTemplateNo;
    String InvSeries;
    String InvNo;
    String InvCode;
    String InvDate;
    String ErrorCode;
    @Column(length = 2000)
    String DescriptionErrorCode;
    String ErrorData;
    String CustomData;
    String createdDate;

    @PrePersist
    public void prePersist() {
        if (createdDate == null) {
            createdDate = LocalDateTime.now().toString();
        }
    }
}
