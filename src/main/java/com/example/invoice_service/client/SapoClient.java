package com.example.invoice_service.client;

import com.example.invoice_service.entity.response.OrderSapoListResponse;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@NoArgsConstructor
@AllArgsConstructor
public class SapoClient {

    @Autowired
    private WebClient webClient;

    @Value("${hugo-sim.sapo.domain}")
    private String domain;

    @Value("${hugo-sim.sapo.api-key}")
    private String apiKey;

    @Value("${hugo-sim.sapo.secret-key}")
    private String secretKey;

    public OrderSapoListResponse callGetApi(Map<String, Object> params) {
        String url = domain;
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder(url);
            sb.append("?");
            params.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
            sb.deleteCharAt(sb.length() - 1); // xóa & cuối cùng
            url = sb.toString();
        }
        return webClient.get()
                .uri(url)
                .headers(headers -> headers.setBasicAuth(apiKey, secretKey))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(OrderSapoListResponse.class)
                .block();
    }
}
