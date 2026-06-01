package com.example.invoice_service.job;

import com.example.invoice_service.client.MisaClient;
import com.example.invoice_service.entity.History;
import com.example.invoice_service.exception.BusinessException;
import com.example.invoice_service.repository.HistoryRepository;
import com.example.invoice_service.service.InvoiceService;
import com.example.invoice_service.service.OrdersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.example.invoice_service.utils.ErrorLogUtil.buildTraceForDb;

@Slf4j
@Component
public class InvoiceJobDaily {

    @Autowired
    HistoryRepository historyRepository;

    @Autowired
    OrdersService ordersService;

    @Autowired
    InvoiceService invoiceService;

    @Autowired
    MisaClient misaClient;

    @Autowired
    TaskScheduler taskScheduler;

    @Scheduled(cron = "0 59 * * * *")
    public void jobSapo() throws Exception {
        log.info("⏰ Job chạy lúc phút 59 của mỗi giờ");
        DateTimeFormatter fmt =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime time = LocalDateTime.now();
        String toDate = time.format(fmt);
        String fromDate = LocalDate.now()
                .minusDays(15)
                .atStartOfDay()
                .withSecond(0)
                .withNano(0)
                .format(fmt);
        try {
            historyRepository.save(
                    History.builder()
                            .type("SYNC_ORDERS_SAPO_AUTO")
                            .value("Đồng bộ đơn hàng vào phút 59 của mỗi giờ")
                            .value2(fromDate + " - " + toDate)
                            .status("SUCCESS")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
            ordersService.syncOrderSapo(fromDate, toDate);
        } catch (Exception e) {
            log.error("sync fail, sẽ retry sau 5 giây");
            Thread.sleep(5000);
                historyRepository.save(
                        History.builder()
                                .type("SYNC_ORDERS_SAPO_AUTO")
                                .value("Retry lại sau 5 giây khi đồng bộ đơn hàng vào phút 59 của mỗi giờ thất bại")
                                .value2(fromDate + " - " + toDate)
                                .status("SUCCESS")
                                .createdDate(LocalDateTime.now().toString())
                                .build());
                ordersService.syncOrderSapo(fromDate, toDate);

        }
        try {
            invoiceAuto(fromDate, toDate, "Xuất hoá đơn vào phút 59 của mỗi giờ");
        } catch (Exception e) {
            log.error("invoice fail, sẽ retry sau 30 giây");
            taskScheduler.schedule(
                    () -> invoiceAuto(fromDate, toDate, "Retry lại sau 30 giây khi xuất hoá đơn vào phút 59 của mỗi giờ thất bại"),
                    Instant.now().plusSeconds(30)
            );
        }
    }

    @Scheduled(cron = "0 59 * * * *")
    public void jobWeb() throws Exception {
        DateTimeFormatter fmt =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime time = LocalDateTime.now();
        String toDate = time.format(fmt);
        String fromDate = LocalDate.now()
                .minusDays(2)
                .atStartOfDay()
                .withSecond(0)
                .withNano(0)
                .format(fmt);
        try {
            historyRepository.save(
                    History.builder()
                            .type("SYNC_ORDERS_WEB_AUTO")
                            .value("Đồng bộ đơn hàng vào phút 59 của mỗi giờ")
                            .value2(fromDate + " - " + toDate)
                            .status("SUCCESS")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
            ordersService.syncOrderWeb(fromDate, toDate);
        } catch (Exception e) {
            log.error("sync fail, sẽ retry sau 5 giây");
            Thread.sleep(5000);
                historyRepository.save(
                        History.builder()
                                .type("SYNC_ORDERS_WEB_AUTO")
                                .value("Retry lại sau 5 giây khi đồng bộ đơn hàng vào phút 59 của mỗi giờ thất bại")
                                .value2(fromDate + " - " + toDate)
                                .status("SUCCESS")
                                .createdDate(LocalDateTime.now().toString())
                                .build());
                ordersService.syncOrderWeb(fromDate, toDate);

        }

        try {
            invoiceAuto(fromDate, toDate, "Xuất hoá đơn vào phút 59 của mỗi giờ");
        } catch (Exception e) {
            taskScheduler.schedule(
                    () -> invoiceAuto(fromDate, toDate, "Retry lại sau 30 giây khi xuất hoá đơn vào phút 59 của mỗi giờ thất bại"),
                    Instant.now().plusSeconds(30)
            );
        }
    }

    @Scheduled(cron = "0 00 00 * * *")
    public void runAt0000EveryDay() {
        misaClient.refreshToken();
        historyRepository.save(
                History.builder()
                        .type("REFRESH_TOKEN_AUTO")
                        .status("SUCCESS")
                        .createdDate(LocalDateTime.now().toString())
                        .build());
    }

    public void invoiceAuto(String fromDate, String toDate, String message) {
        log.info(message);
        try {
            invoiceService.publishInvoiceByDate(null, null, fromDate, toDate);
            historyRepository.save(
                    History.builder()
                            .type("PUBLISH_INVOICE_AUTO")
                            .value(message)
                            .value2(fromDate + " - " + toDate)
                            .status("SUCCESS")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
        } catch (BusinessException ignored) {
        } catch (Exception e) {
            historyRepository.save(
                    History.builder()
                            .type("PUBLISH_INVOICE_AUTO")
                            .value(message)
                            .detailsError(buildTraceForDb(e))
                            .value2(fromDate + " - " + toDate)
                            .status("FAIL")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
            throw e;
        }
    }
}
