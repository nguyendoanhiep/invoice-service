package com.example.invoice_service.entity.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record InvoiceData(
        @JsonProperty("RefID")
        String refId,

        @JsonProperty("InvSeries")
        String invSeries,

        @JsonProperty("InvDate")
        String invDate, // yyyy-MM-dd

        @JsonProperty("CurrencyCode")
        String currencyCode,

        @JsonProperty("IsSendEmail")
        boolean isSendEmail,

        @JsonProperty("ReceiverEmail")
        String receiverEmail,

        @JsonProperty("ExchangeRate")
        double exchangeRate,

        @JsonProperty("PaymentMethodName")
        String paymentMethodName,

        @JsonProperty("IsInvoiceCalculatingMachine")
        boolean isInvoiceCalculatingMachine,

        @JsonProperty("BuyerLegalName")
        String buyerLegalName,

        @JsonProperty("BuyerTaxCode")
        String buyerTaxCode,

        @JsonProperty("BuyerAddress")
        String buyerAddress,

        @JsonProperty("BuyerCode")
        String buyerCode,

        @JsonProperty("BuyerPhoneNumber")
        String buyerPhoneNumber,

        @JsonProperty("BuyerEmail")
        String buyerEmail,

        @JsonProperty("BuyerFullName")
        String buyerFullName,

        @JsonProperty("BuyerBankAccount")
        String buyerBankAccount,

        @JsonProperty("BuyerBankName")
        String buyerBankName,

        @JsonProperty("TotalAmountWithoutVATOC")
        double totalAmountWithoutVATOC,

        @JsonProperty("TotalVATAmountOC")
        double totalVATAmountOC,

        @JsonProperty("TotalDiscountAmountOC")
        double totalDiscountAmountOC,

        @JsonProperty("TotalAmountOC")
        double totalAmountOC,

        @JsonProperty("TotalAmountInWords")
        String TotalAmountInWords,

        @JsonProperty("OriginalInvoiceDetail")
        List<OriginalInvoiceDetail> originalInvoiceDetail,

        @JsonProperty("TaxRateInfo")
        List<TaxRateInfo> taxRateInfo

) { }
