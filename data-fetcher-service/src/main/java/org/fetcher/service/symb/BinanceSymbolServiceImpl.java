package org.fetcher.service.symb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.service.SymbolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceSymbolServiceImpl implements SymbolService {

    @Value("${binance.get-tickets-url}")
    private String binanceGetTicketsUrl;

    @Value("${binance.redis-key}")
    private String binanceRedisKey;

    private final WebClient webClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> fetchSymbols() {
        // Сначала проверяем кэш
        List<String> cached = (List<String>) redisTemplate.opsForValue().get(binanceRedisKey);
        if (cached != null && !cached.isEmpty()) {
            log.info("Using {} cached symbols from Redis", cached.size());
            return cached;
        }
        
        // Если кэш пустой, пытаемся получить через API
        return attemptFetchFromEndpoint(binanceGetTicketsUrl)
                .onErrorResume(e -> {
                    log.warn("Primary endpoint failed: {}, trying alternative endpoint", e.getMessage());
                    // Альтернативный endpoint - более легкий для получения символов
                    String alternativeUrl = "https://api.binance.com/api/v3/ticker/price";
                    return attemptFetchFromAlternativeEndpoint(alternativeUrl);
                })
                .onErrorResume(e -> {
                    log.warn("Alternative endpoint failed: {}, trying third endpoint", e.getMessage());
                    // Третий endpoint - еще более легкий
                    String thirdUrl = "https://api.binance.com/api/v3/ping";
                    return attemptFetchFromThirdEndpoint(thirdUrl);
                })
                .onErrorResume(e -> {
                    log.error("All endpoints failed, using default symbols", e);
                    return Mono.just(getDefaultSymbols());
                })
                .block();
    }

    private Mono<List<String>> attemptFetchFromEndpoint(String url) {
        return webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseSymbols)
                .doOnSuccess(symbols -> {
                    redisTemplate.opsForValue().set(
                            binanceRedisKey,
                            symbols,
                            5,
                            TimeUnit.HOURS
                    );
                    log.info("Fetched {} symbols from Binance via {}", symbols.size(), url);
                })
                .doOnError(e -> log.error("Failed to fetch from {}: {}", url, e.getMessage()));
    }

    private Mono<List<String>> attemptFetchFromAlternativeEndpoint(String url) {
        return webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parsePriceSymbols)
                .doOnSuccess(symbols -> {
                    redisTemplate.opsForValue().set(
                            binanceRedisKey,
                            symbols,
                            5,
                            TimeUnit.HOURS
                    );
                    log.info("Fetched {} symbols from Binance ticker/price", symbols.size());
                })
                .doOnError(e -> log.error("Failed to fetch from alternative endpoint {}: {}", url, e.getMessage()));
    }

    private Mono<List<String>> attemptFetchFromThirdEndpoint(String url) {
        return webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    log.info("Third endpoint responded: {}", response);
                    // Если третий endpoint работает, используем fallback символы
                    return getDefaultSymbols();
                })
                .doOnSuccess(symbols -> {
                    redisTemplate.opsForValue().set(
                            binanceRedisKey,
                            symbols,
                            5,
                            TimeUnit.HOURS
                    );
                    log.info("Using {} default symbols after third endpoint check", symbols.size());
                })
                .doOnError(e -> log.error("Failed to fetch from third endpoint {}: {}", url, e.getMessage()));
    }

    private List<String> getDefaultSymbols() {
        List<String> defaultSymbols = List.of(
                "BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT", "SOLUSDT",
                "DOGEUSDT", "DOTUSDT", "LTCUSDT", "LINKUSDT", "BCHUSDT", "XLMUSDT",
                "UNIUSDT", "ETCUSDT", "FILUSDT", "TRXUSDT", "AVAXUSDT", "ATOMUSDT",
                "SHIBUSDT", "PEPEUSDT", "ARBUSDT", "OPUSDT", "MATICUSDT", "NEARUSDT"
        );
        log.info("Using {} default symbols as fallback", defaultSymbols.size());
        return defaultSymbols;
    }

    private List<String> parseSymbols(String response) {
        try {
            JsonNode tickers = objectMapper.readTree(response);
            List<String> symbolList = new ArrayList<>();

            if (!tickers.isArray()) {
                throw new RuntimeException("Expected array");
            }

            for (JsonNode ticker : tickers) {
                String symbol = ticker.path("symbol").asText();
                String lastPriceStr = ticker.path("lastPrice").asText();
                String volumeStr = ticker.path("quoteVolume").asText();

                if (new BigDecimal(lastPriceStr).compareTo(BigDecimal.ZERO) <= 0) continue;
                if (new BigDecimal(volumeStr).compareTo(new BigDecimal("1000")) < 0) continue;
                
                // Добавляем все активные пары (убрали фильтрацию по типам)
                symbolList.add(symbol);
            }

            log.info("Fetched {} active symbols from /ticker/24hr", symbolList.size());
            return symbolList;
        } catch (Exception e) {
            log.error("Failed to parse", e);
            throw new RuntimeException("Parse error", e);
        }
    }

    private List<String> parsePriceSymbols(String response) {
        try {
            JsonNode prices = objectMapper.readTree(response);
            List<String> symbolList = new ArrayList<>();

            if (!prices.isArray()) {
                throw new RuntimeException("Expected array");
            }

            for (JsonNode price : prices) {
                String symbol = price.path("symbol").asText();
                String priceValue = price.path("price").asText();

                // Добавляем все пары с валидной ценой (убрали фильтрацию по типам)
                if (!priceValue.equals("0.00000000")) {
                    symbolList.add(symbol);
                }
            }

            log.info("Fetched {} active symbols from /ticker/price", symbolList.size());
            return symbolList;
        } catch (Exception e) {
            log.error("Failed to parse ticker/price", e);
            throw new RuntimeException("Parse error", e);
        }
    }

    @Override
    public List<String> getAvailableSymbols() {
        List<String> symbols = (List<String>) redisTemplate.opsForValue().get(binanceRedisKey);
        return symbols != null ? symbols : fetchSymbols();
    }
    
    /**
     * Принудительно обновляет кэш символов, игнорируя существующие данные
     */
    public List<String> refreshSymbols() {
        log.info("Forcing refresh of Binance symbols cache");
        redisTemplate.delete(binanceRedisKey);
        return fetchSymbols();
    }
}