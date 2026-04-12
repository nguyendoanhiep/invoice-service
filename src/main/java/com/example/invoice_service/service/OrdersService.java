package com.example.invoice_service.service;

import com.example.invoice_service.client.HugoSimClient;
import com.example.invoice_service.client.SapoClient;
import com.example.invoice_service.entity.History;
import com.example.invoice_service.entity.Orders;
import com.example.invoice_service.entity.PublishInvoiceItem;
import com.example.invoice_service.entity.Source;
import com.example.invoice_service.entity.request.InfoBuyerRequest;
import com.example.invoice_service.entity.response.*;
import com.example.invoice_service.exception.BusinessException;
import com.example.invoice_service.repository.HistoryRepository;
import com.example.invoice_service.repository.OrderRepository;
import com.example.invoice_service.repository.PublishInvoiceItemRepository;
import com.example.invoice_service.repository.SourceRepository;
import com.example.invoice_service.utils.ErrorLogUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.invoice_service.entity.enums.SystemEnum.HUGO_SIM;
import static com.example.invoice_service.utils.ErrorLogUtil.buildTraceForDb;


@Service
@Slf4j
public class OrdersService {
    @Autowired
    HugoSimClient hugoSimClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PublishInvoiceItemRepository publishInvoiceItemRepository;

    @Autowired
    HistoryRepository historyRepository;

    @Autowired
    SourceRepository sourceRepository;

    @Value("${hugo-sim.web.not-before}")
    String notBefore;

    @Value("${hugo-sim.sapo.not-before}")
    String notBeforeBySapo;

    @Value("${hugo-sim.web.is-auto-daily:false}")
    private boolean isAutoDailyForWeb;

    @Value("${hugo-sim.sapo.is-auto-daily:false}")
    private boolean isAutoDailyForSapo;

    @Autowired
    SapoClient sapoClient;

    public List<PublishInvoiceItem> invoiceHistory(String orderId) {
        return publishInvoiceItemRepository.getAllByOrderId(orderId);
    }

    public List<OrderReportDetails> reportDetails(String systemName, Integer year) {

        LocalDateTime from = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime to = from.plusYears(1);
        return orderRepository.reportDetails(systemName, from,to);
    }

    public String sync(String fromDate, String toDate) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Boolean> webFuture = CompletableFuture.completedFuture(false);
        if (isAutoDailyForWeb) {
            webFuture = executor.submit(() -> {
                try {
                    return syncOrderWeb(fromDate, toDate);
                } catch (Exception e) {
                    log.error("Sync Web lỗi", e);
                    return false;
                }
            });
        }
        Future<Boolean> sapoFuture = CompletableFuture.completedFuture(false);
        if (isAutoDailyForSapo) {
            sapoFuture = executor.submit(() -> {
                try {
                    return syncOrderSapo(fromDate, toDate);
                } catch (Exception e) {
                    log.error("Sync Sapo lỗi", e);
                    return false;
                }
            });
        }

        boolean webResult = false;
        boolean sapoResult = false;

        try {
            webResult = webFuture.get();
            sapoResult = sapoFuture.get();
        } catch (Exception e) {
            ErrorLogUtil.log(e);
        } finally {
            executor.shutdown();
        }

        if (webResult && sapoResult) {
            return "Đồng bộ đơn hàng SAPO và Web thành công";
        }

        if (webResult) {
            return "Đồng bộ Web thành công, SAPO thất bại";
        }

        if (sapoResult) {
            return "Đồng bộ SAPO thành công, Web thất bại";
        }
        throw new BusinessException("400", "Đồng bộ cả hai hệ thống thất bại");
    }
    public void saveSource(Set<String> sourceNames ,String systemName){
        // Lấy toàn bộ name trong DB
        Set<String> dbNames = sourceRepository.findAll()
                .stream()
                .map(Source::getName)
                .collect(Collectors.toSet());

        // Xóa khỏi sourceNames những cái đã tồn tại
        sourceNames.removeIf(dbNames::contains);

        // Phần còn lại là source mới → insert
        List<Source> newSources = sourceNames.stream()
                .map(name -> Source.builder()
                        .name(name)
                        .systemName(systemName)
                        .isPublishInvoice(true)
                        .build())
                .toList();

        sourceRepository.saveAll(newSources);

    }


    public boolean syncOrderSapo(String fromDate, String toDate) {
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
                    order.setId(HUGO_SIM + "_" + orderSapoResponse.id() + "_2");
                    order.setSource(orderSapoResponse.sourceName().toUpperCase());
                    order.setSystemName(HUGO_SIM.name());
                    order.setStatus(orderSapoResponse.financialStatus());
                    order.setCurrency(orderSapoResponse.currency());
                    order.setTotal(orderSapoResponse.totalPrice());
                    order.setDateCreated(orderSapoResponse.createdOn().plusHours(7));
                    order.setDateCompleted(orderSapoResponse.processedOn());
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
            List<String> orderIdsInDB = orderRepository.findAllById(ordersListAll.stream().map(Orders::getId).toList()).stream().map(Orders::getId).toList();
            ordersListAll.removeIf(sapoOrder -> orderIdsInDB.contains(sapoOrder.getId()));
            orderRepository.saveAll(ordersListAll);
            saveSource(ordersListAll.stream().map(Orders::getSource).collect(Collectors.toSet()), HUGO_SIM.name());
            historyRepository.save(
                    History.builder()
                            .type("SYNC_ORDERS_SAPO")
                            .value(ordersListAll.stream()
                                    .map(order -> String.valueOf(order.getId()))
                                    .collect(Collectors.joining(",")))
                            .value2(fromDate + " - " + toDate)
                            .createdDate(LocalDateTime.now().toString())
                            .status("SUCCESS")
                            .build());
            return true;
        } catch (Exception e) {
            ErrorLogUtil.log(e);
            historyRepository.save(
                    History.builder()
                            .type("SYNC_ORDERS_SAPO")
                            .detailsError(buildTraceForDb(e))
                            .value2(fromDate + " - " + toDate)
                            .createdDate(LocalDateTime.now().toString())
                            .status("FAIL")
                            .build());
            throw e;
        }

    }

    public boolean syncOrderWeb(String fromDate, String toDate) {
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
                order.setId(HUGO_SIM + "_" + woo.id() + "_1");
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
            List<String> orderIdsInDB = orderRepository.findAllById(orders.stream().map(Orders::getId).toList()).stream().map(Orders::getId).toList();
            orders.removeIf(order -> orderIdsInDB.contains(order.getId()));

            orderRepository.saveAll(orders);
            saveSource(orders.stream().map(Orders::getSource).collect(Collectors.toSet()) , HUGO_SIM.name());
            historyRepository.save(
                    History.builder()
                            .type("SYNC_ORDERS_WEB")
                            .value(orders.stream()
                                    .map(order -> String.valueOf(order.getId()))
                                    .collect(Collectors.joining(",")))
                            .value2(fromDate + " - " + toDate)
                            .createdDate(LocalDateTime.now().toString())
                            .status("SUCCESS")
                            .build());
            return true;
        } catch (Exception e) {
            ErrorLogUtil.log(e);
            historyRepository.save(
                    History.builder()
                            .type("SYNC_ORDERS_WEB")
                            .detailsError(buildTraceForDb(e))
                            .value2(fromDate + " - " + toDate)
                            .createdDate(LocalDateTime.now().toString())
                            .status("FAIL")
                            .build());
            throw e;
        }
    }

    public Page<Orders> getPageByDate(Pageable pageable, String search, String status, String source, String systemName, String fromDate, String toDate) {
        return orderRepository.getPageByDate(pageable, search, status, source, systemName, LocalDateTime.parse(fromDate), LocalDateTime.parse(toDate));
    }

    public boolean updateInfoBuyer(InfoBuyerRequest infoBuyerRequest) {
        Orders orders = orderRepository.findById(infoBuyerRequest.getId()).orElseThrow(() -> new BusinessException("400", "Đơn hàng không tồn tại trong hệ thống"));
        if (orders.getPublishInvoiceStatus().equalsIgnoreCase("SUCCESS"))
            throw new BusinessException("400", "Đơn hàng đã xuất hoá đơn không thể cập nhập thông tin người mua hàng");
        orders.setFullNameBuyer(infoBuyerRequest.getFullNameBuyer());
        orders.setEmailBuyer(infoBuyerRequest.getEmailBuyer());
        orders.setNumberPhoneBuyer(infoBuyerRequest.getNumberPhoneBuyer());
        orders.setAddressBuyer(infoBuyerRequest.getAddressBuyer());
        orderRepository.save(orders);
        return true;
    }

}
