package org.fetcher.client.binance;

import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.ExchangeClient;
import org.fetcher.domain.TickerData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Instant;

@Service
@Slf4j
public class BinanceRestClient implements ExchangeClient {

    private final WebClient webClient;
    private final String[] symbols;
    private final boolean binanceClientEnabled;

    public BinanceRestClient(@Value("${binance.rest-base-url:https://api.binance.com}") String baseUrl,
                             @Value("${binance.symbols}") String symbols,
                             @Value("${binance.enabled}") boolean binanceClientEnabled) {
        this.webClient = WebClient.create(baseUrl);
        this.symbols = symbols.split(",");
        this.binanceClientEnabled = binanceClientEnabled;
    }

    @Override
    public Mono<TickerData> getTicker(String symbol) {
        return webClient.get()
                .uri("/api/v3/ticker/24hr", uriBuilder -> uriBuilder.queryParam("cryptocurrency", symbol).build())
                .retrieve()
                .bodyToMono(BinanceTickerResponse.class)
                .map(response -> new TickerData(
                        getExchangeName(),
                        response.cryptocurrency(),
                        new BigDecimal(response.lastPrice()),
                        new BigDecimal(response.highPrice()),
                        new BigDecimal(response.lowPrice()),
                        new BigDecimal(response.volume()),
                        new BigDecimal(response.priceChangePercent()),
                        Instant.now()
                ))
                .onErrorResume(ex -> {
                    log.error("Failed to fetch ticker for cryptocurrency {} in BinanceRestClient: {}", symbol, ex);
                    return Mono.empty();
                });
    }

    @Override
    public Flux<TickerData> getAllTickers() {
        return Flux.fromArray(symbols)
                .flatMap(this::getTicker)
                .filter(tickerData -> tickerData != null);
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public boolean isEnabled() {
        return binanceClientEnabled;
    }
}