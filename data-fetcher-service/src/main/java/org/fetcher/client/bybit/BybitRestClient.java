package org.fetcher.client.bybit;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.ExchangeClient;
import org.fetcher.domain.TickerData;
import org.fetcher.service.symb.BybitSymbolServiceImpl;
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
public class BybitRestClient implements ExchangeClient {

    private final WebClient webClient;
    private final boolean bybitClientEnabled;
    private final BybitSymbolServiceImpl bybitSymbolService;

    private List<String> cryptocurrency;

    public BybitRestClient(@Value("${bybit.rest-base-url}") String baseUrl,
                           @Value("${bybit.enabled}") boolean bybitClientEnabled,
                           BybitSymbolServiceImpl bybitSymbolService) {
        this.webClient = WebClient.create(baseUrl);
        this.bybitSymbolService = bybitSymbolService;
        this.bybitClientEnabled = bybitClientEnabled;
    }

    @PostConstruct
    public void init(){
        this.cryptocurrency = bybitSymbolService.getAvailableSymbols();
    }

    @Override
    public Mono<TickerData> getTicker(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v5/market/tickers")
                        .queryParam("category", "linear")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .bodyToMono(BybitTickerResponse.class)
                .map(response -> {
                    if (response.retMsg().equals("OK")) {
                        BybitTickerResult result = response.result();
                        BybitTickerData bybitTickerData = result.list().get(0);
                        String symbol_val = bybitTickerData.symbol();
                        String lastPrice = bybitTickerData.lastPrice();
                        String highPrice = bybitTickerData.highPrice24h();
                        String lowPrice = bybitTickerData.lowPrice24h();
                        String volume = bybitTickerData.volume24h();
                        String priceChangePercent = bybitTickerData.price24hPcnt();

                        return new TickerData(
                                getExchangeName(),
                                symbol_val,
                                lastPrice != null ? new BigDecimal(lastPrice) : BigDecimal.ZERO,
                                highPrice != null ? new BigDecimal(highPrice) : BigDecimal.ZERO,
                                lowPrice != null ? new BigDecimal(lowPrice) : BigDecimal.ZERO,
                                volume != null ? new BigDecimal(volume) : BigDecimal.ZERO,
                                priceChangePercent != null
                                        ? new BigDecimal(priceChangePercent).multiply(new BigDecimal("100"))
                                        : BigDecimal.ZERO,
                                Instant.now()
                        );
                    }
                    return null;
                })
                .onErrorResume(ex -> {
                    log.error("Failed to fetch ticker for symbol {} in BybitRestClient: {}", symbol, ex.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Flux<TickerData> getAllTickers() {
        return Flux.fromIterable(cryptocurrency)
                .flatMap(this::getTicker)
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
