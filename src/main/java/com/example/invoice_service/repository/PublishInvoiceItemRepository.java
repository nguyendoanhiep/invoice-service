package com.example.invoice_service.repository;

import com.example.invoice_service.entity.PublishInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PublishInvoiceItemRepository extends JpaRepository<PublishInvoiceItem , Long> {
    @Query("select p from PublishInvoiceItem p where p.RefID = :orderId order by p.createdDate DESC")
    List<PublishInvoiceItem> getAllByOrderId(String orderId);
}
