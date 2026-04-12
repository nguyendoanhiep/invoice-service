package com.example.invoice_service.repository;

import com.example.invoice_service.entity.History;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface HistoryRepository extends JpaRepository<History,Long> {
    @Query("""
        select h
        from History h
        where (:type is null or h.type = :type)
          and (:status is null or h.status = :status)
          and h.createdDate >= :fromDate
          and h.createdDate <= :toDate
        order by h.createdDate desc
    """)
    Page<History> getAll(Pageable pageable , String type ,String status , String fromDate , String toDate);

}
