package com.example.invoice_service.controller;

import com.example.invoice_service.client.MisaClient;
import com.example.invoice_service.entity.Orders;
import com.example.invoice_service.entity.response.DownloadFileResponse;
import com.example.invoice_service.repository.OrderRepository;
import com.example.invoice_service.entity.response.ApiResponse;
import com.example.invoice_service.service.InvoiceService;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/invoice")
@NoArgsConstructor
public class InvoiceController {

    @Autowired
    InvoiceService invoiceService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    MisaClient misaClient;

    @PostMapping("/publish-invoice-by-date")
    public ApiResponse<?> publishInvoiceByDate(@RequestParam(name = "systemName", required = false) String systemName,
                                               @RequestParam(name = "source",required = false) String source,
                                               @RequestParam(name = "fromDate") String fromDate,
                                               @RequestParam(name = "toDate") String toDate){
        return ApiResponse.builder().data(invoiceService.publishInvoiceByDate(systemName,source,fromDate,toDate)).build();
    }

    @PostMapping("/publish-invoice-by-ids")
    public ApiResponse<?> publishInvoiceByIds(@RequestParam(name = "ids") List<String> ids){
        return ApiResponse.builder().data(invoiceService.publishInvoiceByIds(ids)).build();
    }

    @GetMapping("/publishview")
    public ApiResponse<?> publishView(@RequestParam(name = "transId") String transId){
        return ApiResponse.builder().data(misaClient.publishView(transId)).build();
    }

    @GetMapping("/unpublishview")
    public ApiResponse<?> unpublishview(@RequestParam(name = "id") String id){
        return ApiResponse.builder().data(invoiceService.unpublishview(id)).build();
    }

    @PostMapping("/download-invoice-by-date")
    public ResponseEntity<?> downloadInvoiceByDate(
            @RequestParam(name = "systemName", required = false) String systemName,
            @RequestParam(name = "source",required = false) String source,
            @RequestParam(name = "fromDate") String fromDate,
            @RequestParam(name = "toDate") String toDate
    ) throws IOException {
        List<Orders> orders = orderRepository.getAllByDate(systemName,source,LocalDateTime.parse(fromDate),LocalDateTime.parse(toDate) ,List.of("SUCCESS") ,null);
        if(orders.isEmpty()) return ResponseEntity.ok(ApiResponse.builder().code("400").message("Danh sách đơn hàng trong khoảng thời gian trên chưa xuất hoá đơn").build());

        List<DownloadFileResponse> listFileBase64 = misaClient.downloadInvoice(orders.stream().map(Orders::getTransactionID).toList());
        if(listFileBase64.isEmpty()) return ResponseEntity.ok(ApiResponse.builder().code("400").message("Danh sách đơn hàng trên chưa có hoá đơn , vui lòng xuất hoá đơn trước").build());
        // 1. Tạo folder tạm
        Path tempDir = Files.createTempDirectory("misa-invoices-");

        listFileBase64.forEach(file -> {
            if (StringUtils.isBlank(file.ErrorCode())
                    && StringUtils.isNotBlank(file.Data())) {

                byte[] fileBytes = Base64.getDecoder().decode(file.Data());

                // 2. Đặt tên file (ưu tiên transactionId / refId)
                String fileName = "invoice_" + file.TransactionID() + ".pdf";
                Path filePath = tempDir.resolve(fileName);

                try {
                    Files.write(filePath, fileBytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        // 3. Zip folder
        Path zipPath = Files.createTempFile("misa-invoices-", ".zip");
        zipFolder(tempDir, zipPath);

        // 4. Trả file zip
        Resource resource = new FileSystemResource(zipPath.toFile());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=misa-invoices.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(zipPath))
                .body(resource);
    }

    @PostMapping("/download-invoice-by-ids")
    public ResponseEntity<?> downloadInvoiceByIds(
            @RequestParam(name = "transIds") List<String> transIds
    ) throws IOException {
        List<DownloadFileResponse> listFileBase64 = misaClient.downloadInvoice(transIds);
        if(listFileBase64.isEmpty()) return ResponseEntity.ok(ApiResponse.builder().code("400").message("Danh sách đơn hàng trên chưa có hoá đơn , vui lòng xuất hoá đơn trước").build());
        if (transIds.size() == 1) {
            byte[] fileBytes = null;
            String fileName = null;
            for (var file : listFileBase64) {
                if (StringUtils.isBlank(file.ErrorCode())
                        && StringUtils.isNotBlank(file.Data())) {

                    fileBytes = Base64.getDecoder().decode(file.Data());
                    fileName = "invoice_" + file.TransactionID() + ".pdf";
                }
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(fileBytes);
        }else {
            // 1. Tạo folder tạm
            Path tempDir = Files.createTempDirectory("misa-invoices-");

            listFileBase64.forEach(file -> {
                if (StringUtils.isBlank(file.ErrorCode())
                        && StringUtils.isNotBlank(file.Data())) {

                    byte[] fileBytes = Base64.getDecoder().decode(file.Data());

                    // 2. Đặt tên file (ưu tiên transactionId / refId)
                    String fileName = "invoice_" + file.TransactionID() + ".pdf";
                    Path filePath = tempDir.resolve(fileName);

                    try {
                        Files.write(filePath, fileBytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            // 3. Zip folder
            Path zipPath = Files.createTempFile("misa-invoices-", ".zip");
            zipFolder(tempDir, zipPath);

            // 4. Trả file zip
            Resource resource = new FileSystemResource(zipPath.toFile());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=misa-invoices.zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(zipPath))
                    .body(resource);
        }
    }


    private void zipFolder(Path sourceDir, Path zipFilePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(zipFilePath))) {

            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(
                                sourceDir.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }


}
