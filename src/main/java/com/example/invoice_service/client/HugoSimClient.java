package com.example.invoice_service.client;

import com.example.invoice_service.entity.response.WooResponse;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@NoArgsConstructor
@AllArgsConstructor
public class HugoSimClient {

    @Value("${hugo-sim.web.domain}")
    private String domain;

    @Value("${hugo-sim.web.username}")
    private String consumer_key;

    @Value("${hugo-sim.web.password}")
    private String consumer_secret;

    @Autowired
    private WebClient webClient;

    public <T> WooResponse<T> callGetApi(Map<String,Object> params , Class<T> responseType) {
        String url = domain;
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder(domain);
            sb.append("?");
            params.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
            sb.deleteCharAt(sb.length() - 1); // xóa & cuối cùng
            url = sb.toString();
        }
        return webClient.get()
                .uri(url)
                .headers(headers ->
                        headers.setBasicAuth(consumer_key, consumer_secret))
                .exchangeToMono(response -> {

                    int total = Integer.parseInt(
                            Objects.requireNonNull(response.headers()
                                    .asHttpHeaders()
                                    .getFirst("X-WP-Total"))
                    );

                    int totalPages = Integer.parseInt(
                            Objects.requireNonNull(response.headers()
                                    .asHttpHeaders()
                                    .getFirst("X-WP-TotalPages"))
                    );

                    return response.bodyToFlux(responseType)
                            .collectList()
                            .map(body -> new WooResponse<>(body, total, totalPages));
                })
                .block();
    }
}
