package com.example.invoice_service.client;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.invoice_service.entity.request.InvoiceData;
import com.example.invoice_service.entity.request.PublishInvoiceBody;
import com.example.invoice_service.entity.response.DownloadFileResponse;
import com.example.invoice_service.entity.response.MeInvoiceAuthResponse;
import com.example.invoice_service.entity.response.PublishInvoiceItemResponse;
import com.example.invoice_service.entity.response.PublishInvoiceResponse;
import com.example.invoice_service.exception.BusinessException;
import com.example.invoice_service.utils.ErrorLogUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
@NoArgsConstructor
@AllArgsConstructor
public class MisaClient {

    @Value("${misa.domain}")
    private String domain;

    @Value("${misa.appid}")
    private String appid;

    @Value("${misa.taxcode}")
    private String taxcode;

    @Value("${misa.username}")
    private String username;

    @Value("${misa.password}")
    private String password;

    private String token;

    @Autowired
    WebClient webClient;

    @Autowired
    ObjectMapper objectMapper;


    public String getToken() {
        try {
            if (isTokenExpiringSoon()) {
                refreshToken();
            }
        }
        catch (BusinessException businessException){
            throw businessException;
        }
        catch (Exception e) {
            ErrorLogUtil.log(e);
            token = null;
            refreshToken();
        }
        return token;
    }

    private boolean isTokenExpiringSoon() {
        if (token == null) return true;

        DecodedJWT jwt = JWT.decode(token);
        Instant expiresAt = jwt.getExpiresAt().toInstant();

        return Instant.now().isAfter(expiresAt.minus(1, ChronoUnit.DAYS));
    }

    public void refreshToken() {
        Map<String, String> requestBody = Map.of(
                "appid", appid,
                "taxcode", taxcode,
                "username", username,
                "password", password
        );
        String authUrl = domain + "/auth/token";
        MeInvoiceAuthResponse response = webClient.post()
                .uri(authUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(MeInvoiceAuthResponse.class)
                .block();

        if (response != null && response.success() && response.data() != null) {
            token = response.data(); // đây là JWT token
        }
        if (response != null && !response.success()) {
            throw new BusinessException("401", "Đăng nhập misa thất bại");
        }
    }

    public List<PublishInvoiceItemResponse> publishInvoice(PublishInvoiceBody body) {
        try {
            String url = domain + "/invoice";
            PublishInvoiceResponse response = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(PublishInvoiceResponse.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Null response from MeInvoice API");
            }

            if (!response.success()) {
                throw new RuntimeException("Failed to publish invoice: " + response.descriptionErrorCode());
            }

            // publishInvoiceResult là JSON string, parse ra list
            return objectMapper.readValue(
                    response.publishInvoiceResult(),
                    new TypeReference<>() {
                    }
            );

        }
        catch (BusinessException businessException){
            throw businessException;
        }
        catch (Exception e) {
            ErrorLogUtil.log(e);
            throw new RuntimeException("Error publishing invoice: " + e.getMessage(), e);
        }
    }

    public String publishView(String transId) {
        String url = domain + "/invoice/publishview";
        MeInvoiceAuthResponse response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(transId))
                .retrieve()
                .bodyToMono(MeInvoiceAuthResponse.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Null response from MeInvoice API");
        }
        return response.data();
    }

    public MeInvoiceAuthResponse unpublishview(InvoiceData invoiceData) {
        String url = domain + "/invoice/unpublishview";
        MeInvoiceAuthResponse response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invoiceData)
                .retrieve()
                .bodyToMono(MeInvoiceAuthResponse.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Null response from MeInvoice API");
        }
        return response;
    }

    @SneakyThrows
    public List<DownloadFileResponse> downloadInvoice(List<String> transIds) {
        String url = domain + "/invoice/download";
        MeInvoiceAuthResponse response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transIds)
                .retrieve()
                .bodyToMono(MeInvoiceAuthResponse.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Null response from MeInvoice API");
        }
        var listData = objectMapper.readValue(
                response.data(),
                new TypeReference<List<DownloadFileResponse>>() {
                }
        );

        return listData.stream()
                .filter(r -> StringUtils.isBlank(r.ErrorCode()))
                .toList();
    }
}
