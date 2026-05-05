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

    @Scheduled(cron = "0 58 * * * *")
    public void runAtMinute59EveryHour() throws Exception {
        log.info("⏰ Job chạy lúc phút 59 của mỗi giờ");
        DateTimeFormatter fmt =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime time = LocalDateTime.now();
        String toDate = time.format(fmt);
        String fromDate = LocalDate.now()
                .minusDays(1)
                .atStartOfDay()
                .withSecond(0)
                .withNano(0)
                .format(fmt);
        try {
            ordersAuto(fromDate, toDate, "Đồng bộ đơn hàng vào phút 59 của mỗi giờ");
        } catch (Exception e) {
            log.error("sync fail, sẽ retry sau 5 giây");
            Thread.sleep(5000);
            try {
                ordersAuto(fromDate, toDate, "Retry lại sau 5 giây khi đồng bộ đơn hàng vào phút 59 của mỗi giờ thất bại");
            }catch (Exception ignored){}
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

    @Scheduled(cron = "0 30 00 * * *")
    public void runAt3000EveryDay() throws Exception {
        log.info("🌙 Job chạy lúc 00:30 hàng ngày");
        DateTimeFormatter fmt =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime time = LocalDateTime.now();
        String fromDate = time.minusHours(2).format(fmt);
        String toDate = time.format(fmt);
        try {
            ordersAuto(fromDate, toDate, "Đồng bộ đơn hàng vào 00H30 mỗi ngày");
        } catch (Exception e) {
            log.error("sync fail, sẽ retry sau 5 giây");
            Thread.sleep(5000);
            try {
                ordersAuto(fromDate, toDate, "Retry lại sau 5 giây khi xuất hoá đơn vào 00H30 mỗi ngày thất bại");
            }catch (Exception ignored){}
        }
        try {
            invoiceAuto(fromDate, toDate, "Xuất hoá đơn vào 00H30 mỗi ngày");
        } catch (Exception e) {
            log.error("invoice fail, sẽ retry sau 30 giây");
            taskScheduler.schedule(
                    () -> invoiceAuto(fromDate, toDate, "Retry lại sau 30 giây khi xuất hoá đơn vào 00H30 mỗi ngày thất bại"),
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


    public void ordersAuto(String fromDate, String toDate, String message) {
        try {
            var res = ordersService.sync(fromDate, toDate);
            if (!res.equals("Đồng bộ đơn hàng SAPO và Web thành công")) {
                throw new BusinessException("400", res);
            }
            historyRepository.save(
                    History.builder()
                            .type("SYNC_ORDERS_AUTO")
                            .value(message)
                            .value2(fromDate + " - " + toDate)
                            .status("SUCCESS")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
        } catch (Exception e) {
            historyRepository.save(
                    History.builder()
                            .type("SYNC_ORDERS_AUTO")
                            .value(message)
                            .detailsError(buildTraceForDb(e))
                            .value2(fromDate + " - " + toDate)
                            .status("FAIL")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
            throw e;
        }
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
