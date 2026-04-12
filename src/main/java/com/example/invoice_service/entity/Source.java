package com.example.invoice_service.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "source")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Source {
    @Id
    private String name;
    private String systemName;
    private String cronJobValue;
    private boolean isPublishInvoice;
}
