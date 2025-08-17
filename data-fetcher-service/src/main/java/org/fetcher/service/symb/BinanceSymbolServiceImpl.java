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
        return webClient.get()
                .uri(binanceGetTicketsUrl)
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
                    log.info("Fetched {} symbols from Binance", symbols.size());
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch symbols from Binance", e);
                    List<String> cached = (List<String>) redisTemplate.opsForValue().get(binanceRedisKey);
                    return cached != null ? Mono.just(cached) : Mono.just(List.of());
                })
                .block();
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
                symbolList.add(symbol);
            }

            log.info("Fetched {} active symbols from /ticker/24hr", symbolList.size());
            return symbolList;
        } catch (Exception e) {
            log.error("Failed to parse", e);
            throw new RuntimeException("Parse error", e);
        }
    }

    @Override
    public List<String> getAvailableSymbols() {
        List<String> symbols = (List<String>) redisTemplate.opsForValue().get(binanceRedisKey);
        return symbols != null ? symbols : fetchSymbols();
    }
}