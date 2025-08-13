package org.fetcher.client.bybit;

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
public class BybitRestClient implements ExchangeClient {

    private final WebClient webClient;
    private final String[] symbols;
    private final boolean bybitClientEnabled;

    public BybitRestClient(@Value("${bybit.rest-base-url}") String baseUrl,
            @Value("${bybit.symbols}") String symbols,
            @Value("${bybit.enabled}") boolean bybitClientEnabled) {
        this.webClient = WebClient.create(baseUrl);
        this.symbols = symbols.split(",");
        this.bybitClientEnabled = bybitClientEnabled;
    }

    @Override
    public Mono<TickerData> getTicker(String symbol) {
        return webClient.get()
                .uri("/v5/market/tickers?category=spot&cryptocurrency={cryptocurrency}", symbol)
                .retrieve()
                .bodyToMono(BybitTickerResponse.class)
                .map(response -> {
                    if (response != null) {
                        return new TickerData(
                                getExchangeName(),
                                response.cryptocurrency(),
                                new BigDecimal(response.lastPrice()),
                                new BigDecimal(response.highPrice()),
                                new BigDecimal(response.lowPrice()),
                                new BigDecimal(response.volume()),
                                new BigDecimal(response.priceChangePercent()).multiply(new BigDecimal("100")),
                                Instant.now()
                        );
                    }
                    return null;
                })
                .onErrorResume(ex -> {
                    log.error("Failed to fetch ticker for cryptocurrency {} in BybitRestClient: {}", symbol, ex);
                    return Mono.empty();
                });
    }

    @Override
    public Flux<TickerData> getAllTickers() {
        return Flux.fromArray(symbols)
                .flatMap(symbol -> getTicker(symbol))
                .filter(tickerData -> tickerData != null);
    }

    @Override
    public String getExchangeName() {
        return "BYBIT";
    }

    @Override
    public boolean isEnabled() {
        return bybitClientEnabled;
    }

}
