package org.fetcher.client.kraken;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.ExchangeClient;
import org.fetcher.domain.TickerData;
import org.fetcher.service.symb.KrakenSymbolServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class KrakenRestClient implements ExchangeClient {

    private final WebClient webClient;
    private final KrakenSymbolServiceImpl krakenSymbolService;
    private final boolean krakenClientEnabled;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private List<String> cryptocurrency;

    public KrakenRestClient(@Value("${kraken.rest-base-url}") String baseUrl,
                            KrakenSymbolServiceImpl krakenSymbolService,
                            @Value("${kraken.enabled}") boolean krakenClientEnabled) {
        this.webClient = WebClient.create(baseUrl);
        this.krakenSymbolService = krakenSymbolService;
        this.krakenClientEnabled = krakenClientEnabled;
    }

    @PostConstruct
    public void init() {
        this.cryptocurrency = krakenSymbolService.getAvailableSymbols();
    }

    @Override
    public Mono<TickerData> getTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("Symbol is null or empty");
            return Mono.empty();
        }

        String krakenSymbol = convertToKrakenSymbol(symbol);
        return webClient.get()
                .uri("/0/public/Ticker?pair=" + krakenSymbol)
                .retrieve()
                .bodyToMono(KrakenTickerResponse.class)
                .map(response -> {
                    try {
                        Map<String, KrakenTickerData> result = response.getResult();
                        if (result == null || !result.containsKey(krakenSymbol)) {
                            return null;
                        }

                        KrakenTickerData tickerData = result.get(krakenSymbol);
                        if (tickerData == null) {
                            return null;
                        }

                        return new TickerData(
                                getExchangeName(),
                                symbol,
                                parseBigDecimal(tickerData.getLastPrice().get(0)),
                                parseBigDecimal(tickerData.getHighPrice().get(1)),
                                parseBigDecimal(tickerData.getLowPrice().get(1)),
                                parseBigDecimal(tickerData.getVolume().get(1)),
                                calculatePriceChange(tickerData),
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
        return "KRAKEN";
    }

    @Override
    public boolean isEnabled() {
        return krakenClientEnabled;
    }

    private String convertToKrakenSymbol(String symbol) {
        try {
            String s = symbol.toUpperCase(Locale.ROOT).trim();
            String base;
            String quote;
            if (s.contains("/")) {
                String[] parts = s.split("/");
                base = parts[0];
                quote = parts[1];
            } else {
                base = s.substring(0, Math.max(3, s.length() - 3));
                quote = s.substring(base.length());
            }
            switch (base) {
                case "BTC": base = "XXBT"; break;
                case "ETH": base = "XETH"; break;
                case "LTC": base = "XLTC"; break;
                case "XRP": base = "XXRP"; break;
                default:  break;
            }
            if ("USD".equals(quote)) {
                quote = "ZUSD";
            }
            return base + quote;
        } catch (Exception e) {
            log.error("Error converting symbol {} to Kraken format: {}", symbol, e.getMessage());
            return symbol;
        }
    }

    private BigDecimal calculatePriceChange(KrakenTickerData tickerData) {
        try {
            BigDecimal currentPrice = parseBigDecimal(tickerData.getLastPrice().get(0));
            BigDecimal lowPrice = parseBigDecimal(tickerData.getLowPrice().get(0));
            if (lowPrice.compareTo(ZERO) == 0) {
                return ZERO;
            }
            return currentPrice.subtract(lowPrice)
                    .divide(lowPrice, 4, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
        } catch (Exception e) {
            log.warn("Error calculating price change: {}", e.getMessage());
            return ZERO;
        }
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