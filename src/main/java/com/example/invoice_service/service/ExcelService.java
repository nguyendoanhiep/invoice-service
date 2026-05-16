package com.example.invoice_service.service;

import com.example.invoice_service.client.HugoSimClient;
import com.example.invoice_service.client.SapoClient;
import com.example.invoice_service.entity.History;
import com.example.invoice_service.entity.Orders;
import com.example.invoice_service.entity.response.*;
import com.example.invoice_service.repository.OrderRepository;
import com.example.invoice_service.utils.ErrorLogUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.invoice_service.entity.enums.SystemEnum.HUGO_SIM;
import static com.example.invoice_service.utils.ErrorLogUtil.buildTraceForDb;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelService {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    HugoSimClient hugoSimClient;
    @Autowired
    SapoClient sapoClient;
    @Autowired
    ObjectMapper objectMapper;
    @Value("${hugo-sim.web.not-before}")
    String notBefore;

    @Value("${hugo-sim.sapo.not-before}")
    String notBeforeBySapo;

    public List<Orders> getOrderSapo(String fromDate, String toDate) {
        LocalDateTime from = LocalDateTime.parse(fromDate);
        LocalDateTime notBeforeTime = LocalDateTime.parse(notBeforeBySapo);

        // Nếu fromDate nhỏ hơn notBefore → lấy notBefore
        if (from.isBefore(notBeforeTime)) {
            fromDate = notBeforeBySapo;
        }
        try {
            int limit = 100;
            int page = 1;
            List<Orders> ordersListAll = new ArrayList<>();
            List<Orders> ordersList;

            OffsetDateTime fromZ = LocalDateTime.parse(fromDate).minusHours(7).atOffset(ZoneOffset.UTC);
            OffsetDateTime toZ = LocalDateTime.parse(toDate).minusHours(7).atOffset(ZoneOffset.UTC);
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
            do {
                Map<String, Object> params = new HashMap<>();
                params.put("page", page);
                params.put("limit", limit);
                params.put("financial_status", "paid");
                params.put("created_on_min", fromZ.format(outputFormatter));
                params.put("created_on_max", toZ.format(outputFormatter));
                OrderSapoListResponse sapoListResponse = sapoClient.callGetApi(params);
                ordersList = sapoListResponse.orders().stream().map(orderSapoResponse -> {
                    Orders order = new Orders();
                    order.setId(orderSapoResponse.id().toString());
                    order.setSource(orderSapoResponse.sourceName().toUpperCase());
                    order.setSystemName(HUGO_SIM.name());
                    order.setStatus(orderSapoResponse.financialStatus());
                    order.setCurrency(orderSapoResponse.currency());
                    order.setTotal(orderSapoResponse.totalPrice());
                    order.setDateCreated(orderSapoResponse.createdOn().plusHours(7));
                    order.setDateCompleted(orderSapoResponse.modifiedOn());
                    order.setPaymentMethod(orderSapoResponse.gateway());
                    if (!Objects.isNull(orderSapoResponse.billingAddress())) {
                        String address = Stream.of(
                                orderSapoResponse.billingAddress().address1(),
                                orderSapoResponse.billingAddress().city(),
                                orderSapoResponse.billingAddress().country()
                        ).filter(StringUtils::isNotBlank).collect(Collectors.joining(", "));
                        order.setAddressBuyer(address);
                        String fullName = Stream.of(
                                        orderSapoResponse.billingAddress().lastName(),
                                        orderSapoResponse.billingAddress().firstName()
                                ).filter(StringUtils::isNotBlank)
                                .collect(Collectors.joining(" "));
                        order.setFullNameBuyer(fullName);
                    }
                    order.setEmailBuyer(orderSapoResponse.email());
                    order.setNumberPhoneBuyer(orderSapoResponse.phone());
                    order.setRootSource(orderSapoResponse.sourceName());
                    String staffName = "";
                    if (orderSapoResponse.user() != null) {
                        staffName = orderSapoResponse.user().last_name() + " " + orderSapoResponse.user().first_name();
                    }
                    order.setStaffName(staffName);
                    order.setNote(orderSapoResponse.note());
                    try {
                        List<LineItem> lineItem = orderSapoResponse
                                .lineItems()
                                .stream()
                                .map(
                                        lineItemSapo -> LineItem.builder()
                                                .id(lineItemSapo.id())
                                                .name(lineItemSapo.name())
                                                .sku(lineItemSapo.sku())
                                                .price(lineItemSapo.price().intValueExact())
                                                .quantity(lineItemSapo.quantity())
                                                .total(String.valueOf(lineItemSapo.originalTotal().longValue()))
                                                .build())
                                .toList();
                        order.setLineItems(objectMapper.writeValueAsString(lineItem));
                    } catch (JsonProcessingException e) {
                        log.error(e.getMessage());
                        throw new RuntimeException(e);
                    }
                    return order;
                }).toList();
                ordersListAll.addAll(ordersList);
                page++;
            } while (ordersList.size() == limit);
            return ordersListAll;
        } catch (Exception e) {
            ErrorLogUtil.log(e);
            throw e;
        }
    }

    public List<Orders> getOrderWeb(String fromDate, String toDate) {
        LocalDateTime from = LocalDateTime.parse(fromDate);
        LocalDateTime notBeforeTime = LocalDateTime.parse(notBefore);

        // Nếu fromDate nhỏ hơn notBefore → lấy notBefore
        if (from.isBefore(notBeforeTime)) {
            fromDate = notBefore;
        }
        try {
            int perPage = 100;
            int page = 1;
            int totalPage;

            List<WooOrderResponse> wooOrders = new ArrayList<>();

            do {
                Map<String, Object> params = new HashMap<>();
                params.put("status", "completed");
                params.put("per_page", perPage);
                params.put("page", page);
                params.put("after", fromDate);
                params.put("before", toDate);

                WooResponse<WooOrderResponse> wooResponse =
                        hugoSimClient.callGetApi(params, WooOrderResponse.class);

                wooOrders.addAll(wooResponse.getData());

                totalPage = wooResponse.getTotalPages();

                page++;

            } while (page <= totalPage);
            List<Orders> orders = wooOrders.stream().map(woo -> {
                Orders order = new Orders();
                order.setId(woo.id().toString());
                order.setSource("WEBSITE");
                order.setSystemName(HUGO_SIM.name());
                order.setStatus(woo.status());
                order.setCurrency(woo.currency());
                order.setTotal(BigDecimal.valueOf(Long.parseLong(woo.total())));
                order.setDateCreated(LocalDateTime.parse(woo.date_created()));
                order.setDateCompleted(woo.date_completed());
                order.setPaymentMethod(woo.payment_method());
                if (!Objects.isNull(woo.billing())) {
                    String address = Stream.of(
                            woo.billing().address_1(),
                            woo.billing().city(),
                            woo.billing().country()
                    ).filter(StringUtils::isNotBlank).collect(Collectors.joining(", "));
                    String fullName = Stream.of(
                                    woo.billing().last_name(),
                                    woo.billing().first_name()
                            ).filter(StringUtils::isNotBlank)
                            .collect(Collectors.joining(" "));
                    order.setAddressBuyer(address);
                    order.setFullNameBuyer(fullName);
                }
                order.setEmailBuyer(woo.billing().email());
                order.setNumberPhoneBuyer(woo.billing().phone());
                String sourceRoot = "";
                if (woo.meta_data() != null) {
                    String sourceType = woo.meta_data().stream()
                            .filter(data -> "_wc_order_attribution_source_type"
                                    .equalsIgnoreCase(data.key()))
                            .map(WooOrderResponse.MetaData::value)
                            .findFirst()
                            .orElse("");
                    if (sourceType.equalsIgnoreCase("organic")) {
                        sourceType = "Tự nhiên: ";
                    } else if (sourceType.equalsIgnoreCase("referral")) {
                        sourceType = "Giới thiệu: ";
                    }

                    String sourceValue = woo.meta_data().stream()
                            .filter(data -> "_wc_order_attribution_utm_source"
                                    .equalsIgnoreCase(data.key()))
                            .map(WooOrderResponse.MetaData::value)
                            .findFirst()
                            .orElse("");
                    if (sourceValue.equalsIgnoreCase("(direct)")) {
                        sourceRoot = "Trực tiếp";
                    } else {
                        sourceRoot = sourceType + sourceValue;
                    }
                }
                order.setRootSource(sourceRoot);
                try {
                    order.setLineItems(objectMapper.writeValueAsString(woo.line_items()));
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage());
                    throw new RuntimeException(e);
                }
                return order;
            }).collect(Collectors.toList());

            return orders;
        } catch (Exception e) {
            ErrorLogUtil.log(e);
            throw e;
        }
    }


    public byte[] exportToExcel(String fromDate, String toDate) {
        List<Orders> orderAll = orderRepository.getAllByDate(HUGO_SIM.name(), LocalDateTime.parse(fromDate), LocalDateTime.parse(toDate), List.of("SUCCESS"));
//        var webOrders = getOrderWeb(fromDate, toDate);
//        var sapoOrders = getOrderSapo(fromDate, toDate);
//        orderAll.addAll(webOrders);
//        orderAll.addAll(sapoOrders);
//        orderAll.sort(Comparator.comparing(Orders::getDateCreated).reversed());
        try {
            InputStream is = new ClassPathResource("templates/hugo-sim-report.xlsx").getInputStream();
            Workbook workbook = new XSSFWorkbook(is);
            Sheet sheet = workbook.getSheet("Sheet2");

            // ====== TẠO STYLE ======

            // Style căn giữa
            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorder(centerStyle);

            // Style tiền
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(
                    workbook.createDataFormat().getFormat("#,##0")
            );
            setBorder(moneyStyle);

            // Style ngày
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(
                    workbook.createDataFormat().getFormat("dd/MM/yyyy")
            );
            setBorder(dateStyle);

            // Freeze header
            sheet.createFreezePane(0, 1);

            int rowIdx = 1; // vì row 0 là header
            int stt = 1;

            for (Orders order : orderAll) {

                if (order.getLineItems() == null) continue;
                List<LineItem> lineItems = objectMapper.readValue(
                        order.getLineItems(),
                        new TypeReference<List<LineItem>>() {
                        }
                );
                for (LineItem item : lineItems) {

                    Row row = sheet.createRow(rowIdx++);
                    CellStyle defaultStyle = workbook.createCellStyle();
                    setBorder(defaultStyle);

                    // IDX
                    Cell idx = row.createCell(0);
                    idx.setCellValue(stt);
                    idx.setCellStyle(defaultStyle);

                    // Mã ID
                    Cell id = row.createCell(1);
                    id.setCellStyle(defaultStyle);

                    String idValue = order.getId().split("_")[2];

                    id.setCellValue("hugosim2_vn_" + idValue);

                    // Ngày
                    Cell dateCell = row.createCell(2);
                    LocalDateTime ldt = order.getDateCreated();
                    dateCell.setCellValue(ldt);
                    dateCell.setCellStyle(dateStyle);

                    // Tên
                    Cell name = row.createCell(3);
                    name.setCellValue(
                            order.getFullNameBuyer() != null
                                    ? order.getFullNameBuyer()
                                    : ""
                    );
                    name.setCellStyle(defaultStyle);

                    // Mail
                    Cell mail = row.createCell(4);
                    mail.setCellValue(
                            order.getEmailBuyer()
                    );
                    mail.setCellStyle(defaultStyle);


                    // Điện thoại
                    Cell numberPhone = row.createCell(5);
                    numberPhone.setCellValue(
                            order.getNumberPhoneBuyer()
                    );
                    numberPhone.setCellStyle(defaultStyle);


                    // Tên eSIM
                    Cell productName = row.createCell(6);
                    productName.setCellValue(item.name());
                    productName.setCellStyle(defaultStyle);

                    // Số lượng
                    Cell quantity = row.createCell(7);
                    quantity.setCellValue(item.quantity());
                    quantity.setCellStyle(defaultStyle);

                    // Đơn giá
                    Cell price = row.createCell(8);
                    price.setCellValue(item.price());
                    price.setCellStyle(moneyStyle);

                    // Số ngày
                    Cell soNgayCell = row.createCell(9);
                    String soNgay = getSoNgay(item);
                    if (!soNgay.isBlank()) {
                        soNgayCell.setCellValue(Integer.parseInt(soNgay));
                    }
                    soNgayCell.setCellStyle(centerStyle);


                    //  Dung lượng
                    Cell dungLuong = row.createCell(10);
                    dungLuong.setCellValue(getDungLuong(item));
                    dungLuong.setCellStyle(defaultStyle);

                    //  tổng tiền
                    Cell moneyCell = row.createCell(11);
                    if (item.total() != null && !item.total().isBlank()) {
                        moneyCell.setCellValue(Double.parseDouble(item.total()));
                        moneyCell.setCellStyle(moneyStyle);
                    }
                    //ghi chu
                    Cell ghiChu = row.createCell(12);
                    ghiChu.setCellValue(order.getNote());
                    ghiChu.setCellStyle(defaultStyle);


                    // 10. Nguồn gốc
                    Cell sourceCell = row.createCell(13);
                    sourceCell.setCellValue(order.getRootSource());
                    sourceCell.setCellStyle(defaultStyle);

                    Cell nhanVien = row.createCell(14);
                    nhanVien.setCellValue(order.getStaffName());
                    nhanVien.setCellStyle(defaultStyle);

                    stt++;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i <= 14; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Export Excel failed", e);
        }
    }

    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String getSoNgay(LineItem item) {
        if (item.meta_data() == null) return "";

        return item.meta_data().stream()
                .filter(m -> "pa_so-ngay".equalsIgnoreCase(m.getKey()))
                .map(MetaData::getDisplay_value)
                .findFirst()
                .orElse("");
    }

    private String getDungLuong(LineItem item) {
        if (item.meta_data() == null) return "";

        return item.meta_data().stream()
                .filter(m -> "pa_dung-luong".equalsIgnoreCase(m.getKey()))
                .map(MetaData::getDisplay_value)
                .map(v -> {
                    if (v.contains("(")) {
                        return v.substring(0, v.indexOf("(")).trim();
                    }
                    return v;
                })
                .findFirst()
                .orElse("");
    }
}
