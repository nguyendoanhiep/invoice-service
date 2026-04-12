package com.example.invoice_service.controller;

import com.example.invoice_service.entity.Source;
import com.example.invoice_service.entity.request.InfoBuyerRequest;
import com.example.invoice_service.entity.request.SourceRequest;
import com.example.invoice_service.entity.response.ApiResponse;
import com.example.invoice_service.entity.response.OrderReportDetails;
import com.example.invoice_service.repository.OrderRepository;
import com.example.invoice_service.repository.SourceRepository;
import com.example.invoice_service.service.ExcelService;
import com.example.invoice_service.service.OrdersService;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/order")
@NoArgsConstructor
public class OrdersController {
    @Autowired
    OrdersService ordersService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    SourceRepository sourceRepository;

    @Autowired
    ExcelService excelService;

    @GetMapping("/sync")
    public ApiResponse<?> sync(@RequestParam(name = "fromDate") String fromDate,
                               @RequestParam(name = "toDate") String toDate) {
        return ApiResponse.builder().data(ordersService.sync(fromDate, toDate)).build();
    }

    @GetMapping("/source")
    public ApiResponse<?> source() {
        return ApiResponse.builder().data(sourceRepository.findAll()).build();
    }

    @PostMapping("/update-source")
    public ApiResponse<?> updateSource(@RequestBody SourceRequest sourceRequest) {
        Source source = sourceRepository.findById(sourceRequest.name()).orElseThrow();
        source.setPublishInvoice(sourceRequest.publishInvoice());
        return ApiResponse.builder().data(sourceRepository.save(source)).build();
    }

    @GetMapping("/report")
    public ApiResponse<?> report(@RequestParam(name = "systemName") String systemName,
                                 @RequestParam(name = "year") Integer year,
                                 @RequestParam(name = "month", required = false) Integer month,
                                 @RequestParam(name = "day", required = false) Integer day) {
        LocalDateTime from;
        LocalDateTime to;
        if (month == null) {
            // Chỉ có year
            from = LocalDateTime.of(year, 1, 1, 0, 0);
            to = from.plusYears(1);

        } else if (day == null) {
            // Có year + month
            from = LocalDateTime.of(year, month, 1, 0, 0);
            to = from.plusMonths(1);

        } else {
            // Có year + month + day
            from = LocalDateTime.of(year, month, day, 0, 0);
            to = from.plusDays(1);
        }
        return ApiResponse.builder().data(orderRepository.report(systemName, from, to)).build();
    }

    @GetMapping("/report-details")
    public ApiResponse<?> reportDetails(@RequestParam(name = "systemName") String systemName,
                                        @RequestParam(name = "year") Integer year) {

        return ApiResponse.builder().data(ordersService.reportDetails(systemName, year)).build();
    }

    @GetMapping("/invoice-history")
    public ApiResponse<?> invoiceHistory(@RequestParam(name = "orderId") String orderId) {
        return ApiResponse.builder().data(ordersService.invoiceHistory(orderId)).build();
    }

    @GetMapping
    public ApiResponse<?> getPageByDate(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "systemName", required = false) String systemName,
            @RequestParam(name = "fromDate") String fromDate,
            @RequestParam(name = "toDate") String toDate) {
        return ApiResponse.builder().data(ordersService.getPageByDate(PageRequest.of(page - 1, size), search, status, source, systemName, fromDate, toDate)).build();
    }

    @PostMapping("/update-info-buyer")
    public ApiResponse<?> updateInfoBuyer(@RequestBody InfoBuyerRequest infoBuyerRequest) {
        return ApiResponse.builder().data(ordersService.updateInfoBuyer(infoBuyerRequest)).build();
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam String fromDate,
            @RequestParam String toDate) {

        byte[] excelData = excelService.exportToExcel(fromDate,toDate);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=hugo-sim-report.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }
}
