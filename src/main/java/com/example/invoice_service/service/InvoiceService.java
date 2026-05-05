package com.example.invoice_service.service;

import com.example.invoice_service.client.MisaClient;
import com.example.invoice_service.entity.History;
import com.example.invoice_service.entity.Orders;
import com.example.invoice_service.entity.PublishInvoiceItem;
import com.example.invoice_service.entity.request.InvoiceData;
import com.example.invoice_service.entity.request.OriginalInvoiceDetail;
import com.example.invoice_service.entity.request.PublishInvoiceBody;
import com.example.invoice_service.entity.request.TaxRateInfo;
import com.example.invoice_service.entity.response.*;
import com.example.invoice_service.exception.BusinessException;
import com.example.invoice_service.repository.HistoryRepository;
import com.example.invoice_service.repository.OrderRepository;
import com.example.invoice_service.repository.PublishInvoiceItemRepository;
import com.example.invoice_service.utils.ErrorLogUtil;
import com.example.invoice_service.utils.MoneyToWordsVN;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.invoice_service.utils.ErrorLogUtil.buildTraceForDb;

@Service
@Slf4j
public class InvoiceService {

    @Autowired
    MisaClient misaClient;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PublishInvoiceItemRepository publishInvoiceItemRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    HistoryRepository historyRepository;

    @Value("${misa.template}")
    String template;

    @Value("${misa.send-email-to:info.5staroffices@gmail.com}")
    String misaSendEmailTo;

    @Value("${misa.is-send-email:true}")
    boolean misaIsSendEmail;

    @Value("${send-email-warning-to:hieppnguyenn.dev@gmail.com}")
    String sendEmailWarningTo;

    @Autowired
    MailService mailService;

    public boolean publishInvoiceByIds(List<String> ids) {
        String orderIds = "";
        try {
            List<Orders> orders = orderRepository.getAllByIds(ids);
            if (orders.isEmpty()) throw new BusinessException("400","Không còn đơn hàng nào trong khoảng thời gian này chưa xuất hoá đơn");
            publishInvoice(orders);
            orderIds = orders.stream()
                    .map(order -> String.valueOf(order.getId()))
                    .collect(Collectors.joining(","));
            historyRepository.save(
                    History.builder()
                            .type("PUBLISH_INVOICE")
                            .value(orderIds)
                            .createdDate(LocalDateTime.now().toString())
                            .status("SUCCESS")
                            .build());
            return true;
        }
        catch (BusinessException businessException){
            throw businessException;
        }
        catch (Exception e){
            ErrorLogUtil.log(e);
            historyRepository.save(
                    History.builder()
                            .type("PUBLISH_INVOICE")
                            .detailsError(buildTraceForDb(e))
                            .value(orderIds)
                            .status("FAIL")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
            throw e;
        }
    }

    public String unpublishview(String id){
        Orders order = orderRepository.findById(id).orElseThrow();
        PublishInvoiceBody body = convertOrdersToInvoice(List.of(order));
        MeInvoiceAuthResponse meInvoiceAuthResponse = misaClient.unpublishview(body.invoiceData().get(0));
        if (StringUtils.isBlank(meInvoiceAuthResponse.data())) throw new BusinessException("400","Template khong ton tai");
        return meInvoiceAuthResponse.data();
    }

    public boolean publishInvoiceByDate(String systemName, String source , String fromDate, String toDate) {
        String orderIds = "";
        try{
            List<Orders> orders = orderRepository.getAllByDate(systemName,source,LocalDateTime.parse(fromDate), LocalDateTime.parse(toDate) , List.of("FAIL","INIT") , true);
            if (orders.isEmpty()) throw new BusinessException("400","Không còn đơn hàng nào trong khoảng thời gian này chưa xuất hoá đơn");
            publishInvoice(orders);
            orderIds = orders.stream()
                    .map(order -> String.valueOf(order.getId()))
                    .collect(Collectors.joining(","));
            historyRepository.save(
                    History.builder()
                            .type("PUBLISH_INVOICE")
                            .value(orderIds)
                            .value2(fromDate + " - " +toDate)
                            .status("SUCCESS")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
            return true;
        }
        catch (BusinessException businessException){
            throw businessException;
        }
        catch (Exception e){
            ErrorLogUtil.log(e);
            historyRepository.save(
                    History.builder()
                            .type("PUBLISH_INVOICE")
                            .detailsError(buildTraceForDb(e))
                            .value(orderIds)
                            .value2(fromDate + " - " +toDate)
                            .status("FAIL")
                            .createdDate(LocalDateTime.now().toString())
                            .build());
            throw e;
        }

    }

    public void publishInvoice(List<Orders> orders){
        PublishInvoiceBody body = convertOrdersToInvoice(orders);
        List<PublishInvoiceItemResponse> publishInvoiceResponseItems = misaClient.publishInvoice(body);
        List<PublishInvoiceItem> publishInvoiceItems = publishInvoiceResponseItems.stream().map(
                publishInvoiceItemResponse -> {
                    Orders orderChange = orderRepository.findById(publishInvoiceItemResponse.RefID()).get();
                    if (StringUtils.isBlank(publishInvoiceItemResponse.ErrorCode()) ||
                    "DuplicateInvoiceRefID".equalsIgnoreCase(publishInvoiceItemResponse.ErrorCode())) {
                        orderChange.setPublishInvoiceStatus("SUCCESS");
                        orderChange.setTransactionID(publishInvoiceItemResponse.TransactionID());
                        orderChange.setIssueDateInvoice(LocalDate.now().toString());
                        InvoiceData invoiceData = body.invoiceData().stream().filter(data -> data.refId().equalsIgnoreCase(publishInvoiceItemResponse.RefID())).findFirst().orElse(InvoiceData.builder().build());
                        orderChange.setOriginalAmount(BigDecimal.valueOf(invoiceData.totalAmountWithoutVATOC()));
                        orderChange.setVatAmount(BigDecimal.valueOf(invoiceData.totalVATAmountOC()));
                    } else {
                        if(!orderChange.getPublishInvoiceStatus().equals("SUCCESS")){
                            orderChange.setPublishInvoiceStatus("FAIL");
                            Arrays.asList(sendEmailWarningTo.split(" ")).forEach(to -> CompletableFuture.runAsync(() ->
                                    mailService.sendMail(
                                            to,
                                            "Đơn hàng : " + orderChange.getId() + " xuất hoá đơn thất bại"
                                    )
                            ));
                        }
                    }
                    orderRepository.save(orderChange);
                    return PublishInvoiceItem
                            .builder()
                            .RefID(publishInvoiceItemResponse.RefID())
                            .TransactionID(publishInvoiceItemResponse.TransactionID())
                            .InvTemplateNo(publishInvoiceItemResponse.InvTemplateNo())
                            .InvSeries(publishInvoiceItemResponse.InvSeries())
                            .InvNo(publishInvoiceItemResponse.InvNo())
                            .InvCode(publishInvoiceItemResponse.InvCode())
                            .InvDate(publishInvoiceItemResponse.InvDate())
                            .ErrorCode(publishInvoiceItemResponse.ErrorCode())
                            .DescriptionErrorCode(publishInvoiceItemResponse.DescriptionErrorCode())
                            .ErrorData(publishInvoiceItemResponse.ErrorData())
                            .CustomData(publishInvoiceItemResponse.CustomData())
                            .build();

                }).toList();
        publishInvoiceItemRepository.saveAll(publishInvoiceItems);
    }

    public PublishInvoiceBody convertOrdersToInvoice(List<Orders> orders) {
        List<InvoiceData> invoiceDatas = new ArrayList<>();
        orders.forEach(order -> {
            List<LineItem> lineItems;
            try {
                lineItems = objectMapper.readValue(
                        order.getLineItems(),
                        new TypeReference<>() {
                        }
                );
            } catch (JsonProcessingException e) {
                ErrorLogUtil.log(e);
                throw new RuntimeException(e);
            }

            String address = order.getAddressBuyer();
            List<String> missingInfos = new ArrayList<>();
            String fullName = Normalizer.normalize(order.getFullNameBuyer(), Normalizer.Form.NFKC)
                    .replaceAll("[^a-zA-Z0-9À-ỹ ]", "");
            if (StringUtils.isBlank(fullName)) {
                missingInfos.add("Tên");
            }
            if (StringUtils.isBlank(address)) {
                missingInfos.add("Địa chỉ");
            }
            if (StringUtils.isBlank(order.getNumberPhoneBuyer())) {
                missingInfos.add("SĐT");
            }
            String moneyWords = MoneyToWordsVN.toWords(order.getTotal().longValue()) + " đồng ";
            if (!missingInfos.isEmpty()) {
                String note = "Khách hàng không cung cấp " + String.join(" , ", missingInfos);
                fullName += " (" + note + ")";
            }
            var originalInvoiceDetail = mapToOriginalInvoiceDetails(lineItems);
            List<TaxRateInfo> taxRateInfos = originalInvoiceDetail.stream()
                    .filter(d -> Set.of("0%", "10%").contains(d.vatRateName()))
                    .collect(Collectors.groupingBy(
                            OriginalInvoiceDetail::vatRateName,
                            Collectors.reducing(
                                    new TaxRateInfo("", 0, 0),
                                    d -> new TaxRateInfo(
                                            d.vatRateName(),
                                            d.amountWithoutVAT(),
                                            d.vatAmount()
                                    ),
                                    (a, b) -> new TaxRateInfo(
                                            a.vatRateName().isEmpty() ? b.vatRateName() : a.vatRateName(),
                                            a.amountWithoutVATOC() + b.amountWithoutVATOC(),
                                            a.vatAmountOC() + b.vatAmountOC()
                                    )
                            )
                    ))
                    .values()
                    .stream()
                    .toList();
            double originalPrice = taxRateInfos.stream()
                    .mapToDouble(TaxRateInfo::amountWithoutVATOC)
                    .sum();

            double vatAmount = taxRateInfos.stream()
                    .mapToDouble(TaxRateInfo::vatAmountOC)
                    .sum();
            InvoiceData invoiceData = InvoiceData
                    .builder()
                    .refId(order.getId())
                    .invSeries(template)
                    .isSendEmail(misaIsSendEmail)
                    .receiverEmail(misaSendEmailTo)
                    .invDate(LocalDate.now().toString())
                    .currencyCode("VND")
                    .exchangeRate(1.0)
                    .paymentMethodName("TM/CK")
                    .isInvoiceCalculatingMachine(true)
                    .buyerAddress(address)
                    .buyerFullName(fullName)
                    .buyerEmail(order.getEmailBuyer())
                    .buyerPhoneNumber(order.getNumberPhoneBuyer())
                    .totalAmountOC(order.getTotal().longValue())
                    .totalAmountWithoutVATOC(Double.parseDouble(String.valueOf(originalPrice)))
                    .totalVATAmountOC(Double.parseDouble(String.valueOf(vatAmount)))
                    .originalInvoiceDetail(originalInvoiceDetail)
                    .TotalAmountInWords(moneyWords)
                    .taxRateInfo(taxRateInfos)
                    .build();
            invoiceDatas.add(invoiceData);
        });
        return new PublishInvoiceBody(
                2,
                invoiceDatas,
                null
        );
    }

    public BigDecimal calculateOriginalPrice(BigDecimal totalPrice, BigDecimal taxRate) {
        BigDecimal taxMultiplier = BigDecimal.ONE.add(
                taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
        );

        return totalPrice.divide(taxMultiplier, 0, RoundingMode.HALF_UP);
    }

    public List<OriginalInvoiceDetail> mapToOriginalInvoiceDetails(List<LineItem> lineItems) {
        AtomicInteger lineNumber = new AtomicInteger(1);
        AtomicInteger sortOrder = new AtomicInteger(1);
        return lineItems.stream()
                .map(item -> {
                    StringBuilder fullName = new StringBuilder(item.name());
                    var metaDataList = item.meta_data();
                    if (metaDataList != null) {
                        if (metaDataList.size() > 1) {
                            MetaData metaDataDungLuong = metaDataList.get(1);
                            fullName.append(", Gói dữ liệu: ");
                            fullName.append(metaDataDungLuong.getDisplay_value());
                            fullName.append(",");
                        }
                        if (!metaDataList.isEmpty()) {
                            MetaData metaDataThoiHan = metaDataList.get(0);
                            fullName.append(" Thời hạn: ");
                            fullName.append(metaDataThoiHan.getDisplay_value());
                            fullName.append(" ").append(metaDataThoiHan.getDisplay_key().replace("Số",""));
                        }
                    }
                    String rate = "0";
                    BigDecimal vatAmount = BigDecimal.valueOf(0);
                    BigDecimal originalPrice = BigDecimal.valueOf(Long.parseLong(item.total()));
                    BigDecimal unitPriceOri =  BigDecimal.valueOf(item.price());
                    if(containsVietNam(item.name())){
                        rate = "10";
                        originalPrice = calculateOriginalPrice(BigDecimal.valueOf(Long.parseLong(item.total())), BigDecimal.valueOf(Long.parseLong(rate)));
                        unitPriceOri = calculateOriginalPrice(BigDecimal.valueOf(item.price().longValue()), BigDecimal.valueOf(Long.parseLong(rate)));
                        vatAmount = BigDecimal.valueOf(Long.parseLong(item.total())).subtract(originalPrice);
                    }
                    return new OriginalInvoiceDetail(
                            1,                              // ItemType
                            lineNumber.getAndIncrement(),   // LineNumber
                            sortOrder.getAndIncrement(),   // sortOrder
                            item.sku(),                     // ItemCode
                            fullName.toString(),                    // ItemName
                            "Gói",                        // UnitName (fix cứng)
                            item.quantity(),                // Quantity
                            unitPriceOri.doubleValue(),          // UnitPrice
                            0,                              // DiscountRate
                            0,                              // DiscountAmountOC
                            originalPrice.doubleValue(), // Amount
                            originalPrice.doubleValue(), // AmountOC
                            originalPrice.doubleValue(), // AmountWithoutVAT
                            originalPrice.doubleValue(), // AmountWithoutVATOC
                            rate + "%" ,                      // VATRateName
                            vatAmount.doubleValue(),
                            vatAmount.doubleValue()
                    );
                })
                .toList();
    }

    public static boolean containsVietNam(String input) {
        if (input == null) return false;

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String noAccent = Pattern
                .compile("\\p{InCombiningDiacriticalMarks}+")
                .matcher(normalized)
                .replaceAll("")
                .toLowerCase();

        return noAccent.contains("viet nam");
    }
}
