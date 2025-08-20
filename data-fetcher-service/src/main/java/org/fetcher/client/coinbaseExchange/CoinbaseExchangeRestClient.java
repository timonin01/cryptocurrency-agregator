package org.fetcher.client.coinbaseExchange;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.ExchangeClient;
import org.fetcher.domain.TickerData;
import org.fetcher.service.symb.CoinbaseExchangeSymbolServiceImpl;
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
public class CoinbaseExchangeRestClient implements ExchangeClient {

    private final WebClient webClient;
    private final CoinbaseExchangeSymbolServiceImpl coinbaseExchangeSymbolService;
    private final boolean coinbaseExchangeRestClientEnabled;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private List<String> cryptocurrency;

    public CoinbaseExchangeRestClient(@Value("${coinbaseExchange.rest-base-url:https://api.exchange.coinbase.com}") String coinbaseExchangeRestBaseUrl,
                                      @Value("${coinbaseExchange.rest-enabled:false}") boolean coinbaseExchangeRestClientEnabled,
                                      CoinbaseExchangeSymbolServiceImpl coinbaseExchangeSymbolService) {
        this.webClient = WebClient.builder()
                .baseUrl(coinbaseExchangeRestBaseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Language", "en-US,en;q=0.9")
                .build();
        this.coinbaseExchangeRestClientEnabled = coinbaseExchangeRestClientEnabled;
        this.coinbaseExchangeSymbolService = coinbaseExchangeSymbolService;
    }

    @PostConstruct
    public void init() {
        if (coinbaseExchangeRestClientEnabled) {
            this.cryptocurrency = coinbaseExchangeSymbolService.getAvailableSymbols();
            log.info("Loaded {} symbols for Coinbase Exchange REST", cryptocurrency.size());
            if (cryptocurrency.size() > 0) {
                log.info("First 5 symbols: {}", cryptocurrency.subList(0, Math.min(5, cryptocurrency.size())));
            }
        } else {
            log.warn("Coinbase Exchange REST client is disabled");
        }
    }

    @Override
    public Mono<TickerData> getTicker(String symbol) {
        if (!coinbaseExchangeRestClientEnabled) {
            log.debug("Coinbase Exchange REST client is disabled");
            return Mono.empty();
        }

        if (symbol == null || symbol.isBlank()) {
            log.warn("Symbol is null or empty");
            return Mono.empty();
        }

        String coinbaseSymbol = formatSymbolToCoinbase(symbol);
        if (coinbaseSymbol == null) {
            log.warn("Invalid symbol format: {}", symbol);
            return Mono.empty();
        }
        
        log.info("Fetching ticker for symbol: {} -> {}", symbol, coinbaseSymbol);

        return Mono.zip(
                getTickerData(coinbaseSymbol),
                getStatsData(coinbaseSymbol)
        ).map(tuple -> {
            CoinbaseTickerResponse tickerResponse = tuple.getT1();
            CoinbaseExchangeStatsResponse statsResponse = tuple.getT2();

            return new TickerData(
                    getExchangeName(),
                    symbol,
                    parseBigDecimal(tickerResponse.price()),
                    parseBigDecimal(statsResponse.high()),
                    parseBigDecimal(statsResponse.low()),
                    parseBigDecimal(tickerResponse.volume24h()),
                    calculatePriceChangePercent(
                            parseBigDecimal(statsResponse.open()),
                            parseBigDecimal(tickerResponse.price())
                    ),
                    parseBigDecimal(statsResponse.open()),
                    calculateWeightedAvg(
                            parseBigDecimal(tickerResponse.bid()),
                            parseBigDecimal(tickerResponse.ask()),
                            parseBigDecimal(tickerResponse.price())
                    ),
                    0L,
                    Instant.now()
            );
        }).onErrorResume(ex -> {
            log.error("Failed to fetch ticker for symbol {}: {}", symbol, ex.getMessage());
            log.error("Full error details:", ex);
            return Mono.empty();
        });
    }


    private Mono<CoinbaseTickerResponse> getTickerData(String coinbaseSymbol) {
        log.debug("Fetching ticker data for symbol: {}", coinbaseSymbol);
        return webClient.get()
                .uri("/products/{product_id}/ticker", coinbaseSymbol)
                .retrieve()
                .bodyToMono(CoinbaseTickerResponse.class)
                .doOnSuccess(response -> log.debug("Successfully fetched ticker data for {}: price={}", coinbaseSymbol, response.price()))
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch ticker for {}: {}", coinbaseSymbol, ex.getMessage());
                    return Mono.just(new CoinbaseTickerResponse(null, "0", "0", null, "0", "0", "0"));
                });
    }

    private Mono<CoinbaseExchangeStatsResponse> getStatsData(String coinbaseSymbol) {
        log.debug("Fetching stats data for symbol: {}", coinbaseSymbol);
        return webClient.get()
                .uri("/products/{product_id}/stats", coinbaseSymbol)
                .retrieve()
                .bodyToMono(CoinbaseExchangeStatsResponse.class)
                .doOnSuccess(response -> log.debug("Successfully fetched stats data for {}: high={}, low={}, open={}", 
                    coinbaseSymbol, response.high(), response.low(), response.open()))
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch stats for {}: {}", coinbaseSymbol, ex.getMessage());
                    return Mono.just(new CoinbaseExchangeStatsResponse("0", "0", "0", "0", "0", "0"));
                });
    }

    private String formatSymbolToCoinbase(String symbol) {
        if (symbol == null || symbol.length() < 4) {
            return null;
        }
        
        // Если символ уже в формате "BTC-USD", возвращаем как есть
        if (symbol.contains("-")) {
            return symbol;
        }
        
        // Преобразуем символы в формат Coinbase
        // BTCUSD -> BTC-USD
        // ETHUSDT -> ETH-USD (Coinbase не поддерживает USDT)
        // ADAUSD -> ADA-USD
        
        // Определяем base и quote более гибко
        String base, quote;
        
        if (symbol.endsWith("USD")) {
            base = symbol.substring(0, symbol.length() - 3);
            quote = "USD";
        } else if (symbol.endsWith("USDT")) {
            base = symbol.substring(0, symbol.length() - 4);
            quote = "USD"; // Coinbase использует USD вместо USDT
        } else if (symbol.endsWith("BTC")) {
            base = symbol.substring(0, symbol.length() - 3);
            quote = "BTC";
        } else if (symbol.endsWith("ETH")) {
            base = symbol.substring(0, symbol.length() - 3);
            quote = "ETH";
        } else {
            // Для остальных случаев используем простую логику
            if (symbol.length() >= 6) {
                base = symbol.substring(0, 3);
                quote = symbol.substring(3);
            } else {
                return null; // Слишком короткий символ
            }
        }
        
        return base + "-" + quote;
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value != null ? new BigDecimal(value) : ZERO;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal value: {}", value);
            return ZERO;
        }
    }

    private BigDecimal calculatePriceChangePercent(BigDecimal openPrice, BigDecimal currentPrice) {
        if (openPrice == null || currentPrice == null ||
                openPrice.compareTo(ZERO) == 0 ||
                currentPrice.compareTo(ZERO) == 0) {
            return ZERO;
        }

        try {
            BigDecimal change = currentPrice.subtract(openPrice);
            return change.divide(openPrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(HUNDRED);
        } catch (Exception e) {
            log.warn("Failed to calculate price change percent: {}", e.getMessage());
            return ZERO;
        }
    }

    private BigDecimal calculateWeightedAvg(BigDecimal bid, BigDecimal ask, BigDecimal fallbackPrice) {
        if (bid != null && ask != null &&
                bid.compareTo(ZERO) > 0 && ask.compareTo(ZERO) > 0) {
            try {
                return bid.add(ask).divide(new BigDecimal("2"));
            } catch (Exception e) {
                return fallbackPrice;
            }
        }
        return fallbackPrice;
    }

    @Override
    public Flux<TickerData> getAllTickers() {
        if (!coinbaseExchangeRestClientEnabled) {
            log.debug("Coinbase Exchange REST client is disabled, returning empty flux");
            return Flux.empty();
        }
        
        log.info("Fetching all tickers for Coinbase Exchange, {} symbols", cryptocurrency.size());
        if (cryptocurrency.size() > 0) {
            log.info("First 10 symbols: {}", cryptocurrency.subList(0, Math.min(10, cryptocurrency.size())));
        }
        return Flux.fromIterable(cryptocurrency)
                .flatMap(this::getTicker)
                .filter(tickerData -> tickerData != null)
                .doOnComplete(() -> log.info("Completed fetching tickers for Coinbase Exchange"));
    }

    @Override
    public String getExchangeName() {
        return "COINBASE_EXCHANGE";
    }

    @Override
    public boolean isEnabled() {
        return coinbaseExchangeRestClientEnabled;
    }
}