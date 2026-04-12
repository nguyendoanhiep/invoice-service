package com.example.invoice_service.repository;

import com.example.invoice_service.entity.Orders;
import com.example.invoice_service.entity.response.OrderReportDetails;
import com.example.invoice_service.entity.response.OrderReportProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Orders,String> {

    @Query("""
    select o
    from Orders o
    join Source s
    on o.source = s.name
    where o.dateCreated >= :fromDate
    and o.dateCreated <= :toDate
    and o.publishInvoiceStatus in (:statusList)
    and (:systemName is null or o.systemName = :systemName)
    and (:source is null or o.source = :source)
    and (:isPublishInvoice is null or s.isPublishInvoice = :isPublishInvoice)
    """)
    List<Orders> getAllByDate(String systemName , String source , LocalDateTime fromDate , LocalDateTime toDate ,List<String>statusList , Boolean isPublishInvoice);


    @Query("""
    select o
    from Orders o
    where o.dateCreated >= :fromDate
    and o.dateCreated <= :toDate
    and o.publishInvoiceStatus in (:statusList)
    and (:systemName is null or o.systemName = :systemName)
    order by o.dateCreated DESC
    """)
    List<Orders> getAllByDate(String systemName, LocalDateTime fromDate , LocalDateTime toDate ,List<String>statusList);


    @Query("""
    select o
    from Orders o
    join Source s
    on o.source = s.name
    where o.publishInvoiceStatus != 'SUCCESS'
    and o.id in (:ids)
    and s.isPublishInvoice = true
    """)
    List<Orders> getAllByIds(List<String> ids);

    @Query("""
    select o
    from Orders o
    where o.dateCreated >= :fromDate
    and o.dateCreated <= :toDate
    and (:status is null or o.publishInvoiceStatus = :status)
    and (:source is null or o.source = :source)
    and (:systemName is null or o.systemName = :systemName)
        and (
        :search is null
        or cast(o.id as string) like concat('%', :search, '%')
        or coalesce(o.transactionID, '') like concat('%', :search, '%')
        or coalesce(o.fullNameBuyer, '') like concat('%', :search, '%')
        or coalesce(o.emailBuyer, '') like concat('%', :search, '%')
        or coalesce(o.numberPhoneBuyer, '') like concat('%', :search, '%')
      )
    order by o.dateCreated DESC
    """)
    Page<Orders> getPageByDate(Pageable pageable ,
                               String search ,
                               String status ,
                               String source ,
                               String systemName ,
                               LocalDateTime fromDate ,
                               LocalDateTime toDate);

    @Query(value = """
    SELECT 
        o.source AS source,
        COUNT(o.id) AS totalOrders,
        COALESCE(SUM(CAST(o.total AS DECIMAL(18,2))), 0) AS totalAmount,
        COALESCE(SUM(CAST(o.original_amount AS DECIMAL(18,2))), 0) AS totalOriginalAmount,
        COALESCE(SUM(CAST(o.vat_amount AS DECIMAL(18,2))), 0) AS totalVatAmount
    FROM orders o
    WHERE o.system_name = :systemName
       AND o.date_created >= :fromDate
       AND o.date_created < :toDate
    GROUP BY o.source
    ORDER BY o.source
""", nativeQuery = true)
    List<OrderReportProjection> report(
            @Param("systemName") String systemName,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );


    @Query(value = """
    SELECT 
        o.system_name AS name,
        MONTH(o.date_created) AS month,
        o.source AS source,
        COUNT(o.id) AS totalOrders,
        SUM(CAST(o.total AS DECIMAL(18,2))) AS totalAmount,
        SUM(CAST(o.original_amount AS DECIMAL(18,2))) AS totalOriginalAmount,
        SUM(CAST(o.vat_amount AS DECIMAL(18,2))) AS totalVatAmount
    FROM orders o
    WHERE o.system_name = :systemName
       AND o.date_created >= :fromDate
       AND o.date_created < :toDate
    GROUP BY MONTH(o.date_created), o.source, o.system_name
    ORDER BY month ASC
""", nativeQuery = true)
    List<OrderReportDetails> reportDetails(
            @Param("systemName") String systemName,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

}
