package com.example.invoice_service.repository;

import com.example.invoice_service.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source,String> {
}
