package org.fetcher.client.binance;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.ExchangeClient;
import org.fetcher.domain.TickerData;
import org.fetcher.client.binance.BinanceTickerResponse;
import org.fetcher.service.symb.BinanceSymbolServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class BinanceRestClient implements ExchangeClient {

    private final WebClient webClient;
    private final BinanceSymbolServiceImpl binanceSymbolService;
    private final boolean binanceClientEnabled;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private List<String> cryptocurrency;

    public BinanceRestClient(@Value("${binance.rest-base-url:https://api.binance.com}") String baseUrl,
                             BinanceSymbolServiceImpl binanceSymbolService,
                             @Value("${binance.enabled}") boolean binanceClientEnabled) {
        this.webClient = WebClient.create(baseUrl);
        this.binanceSymbolService = binanceSymbolService;
        this.binanceClientEnabled = binanceClientEnabled;
    }

    @PostConstruct
    public void init() {
        this.cryptocurrency = binanceSymbolService.getAvailableSymbols();
    }

    @Override
    public Mono<TickerData> getTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("Symbol is null or empty");
            return Mono.empty();
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/ticker/24hr")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .bodyToMono(BinanceTickerResponse.class)
                .map(response -> {
                    try {
                        return new TickerData(
                                getExchangeName(),
                                response.symbol(),
                                parseBigDecimal(response.lastPrice()),
                                parseBigDecimal(response.highPrice()),
                                parseBigDecimal(response.lowPrice()),
                                parseBigDecimal(response.volume()),
                                parseBigDecimal(response.priceChangePercent()).multiply(HUNDRED),
                                Instant.now()
                        );
                    } catch (Exception e) {
                        log.error("Error parsing ticker data for {}: {}", symbol, e.getMessage());
                        return null;
                    }
                })
                .onErrorResume(ex -> {
                    log.error("Failed to fetch ticker for symbol {}: {}", symbol, ex.getMessage());
                    return Mono.empty();
                })
                .filter(tickerData -> tickerData != null);
    }

    @Override
    public Flux<TickerData> getAllTickers() {
        return Flux.fromIterable(cryptocurrency)
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

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value != null ? new BigDecimal(value) : ZERO;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal value: {}", value);
            return ZERO;
        }
    }
}