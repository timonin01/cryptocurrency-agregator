package org.fetcher.client.binance;

import lombok.extern.slf4j.Slf4j;
import org.fetcher.domain.TickerData;
import org.fetcher.domain.BinanceTickerResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Instant;

@Service
@Slf4j
public class BinanceRestClient {

    private final WebClient webClient;

    public BinanceRestClient(@Value("${binance.rest-base-url:https://api.binance.com}") String baseUrl) {
        this.webClient = WebClient.create(baseUrl);
    }

    public Mono<TickerData> getTicker(String symbol) {
        return webClient.get()
                .uri("/api/v3/ticker/24hr", uriBuilder -> uriBuilder.queryParam("symbol", symbol).build())
                .retrieve()
                .bodyToMono(BinanceTickerResponse.class)
                .map(response -> new TickerData(
                        response.symbol(),
                        new BigDecimal(response.lastPrice()),
                        new BigDecimal(response.highPrice()),
                        new BigDecimal(response.lowPrice()),
                        new BigDecimal(response.volume()),
                        new BigDecimal(response.priceChangePercent()),
                        Instant.now()
                ))
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch ticker for symbol: {}", symbol, ex);
                    return Mono.empty(); // ← честно: данных нет
                });
    }
}