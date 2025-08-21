package org.fetcher.service.symb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.service.SymbolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KrakenSymbolServiceImpl implements SymbolService {

    @Value("${kraken.get-tickets-url}")
    private String krakenGetTicketsUrl;

    @Value("${kraken.redis-key}")
    private String krakenRedisKey;

    private final WebClient webClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> fetchSymbols() {
        return webClient.get()
                .uri(krakenGetTicketsUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseSymbols)
                .doOnSuccess(symbols -> {
                    redisTemplate.opsForValue().set(
                            krakenRedisKey,
                            symbols,
                            5,
                            TimeUnit.HOURS
                    );
                    log.info("Fetched {} symbols from Kraken", symbols.size());
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch symbols from Kraken", e);
                    return Mono.error(new RuntimeException("Failed to fetch symbols from Kraken", e));
                })
                .block();
    }

    private List<String> parseSymbols(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("result");
            List<String> symbolList = new ArrayList<>();

            result.fields().forEachRemaining(entry -> {
                String symbol = entry.getKey();
                if (!symbol.startsWith(".")) {
                    symbolList.add(symbol);
                }
            });
            return symbolList;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse symbols", e);
        }
    }

    @Override
    public List<String> getAvailableSymbols() {
        List<String> symbols = (List<String>) redisTemplate.opsForValue().get(krakenRedisKey);
        return symbols != null ? symbols : fetchSymbols();
    }
}