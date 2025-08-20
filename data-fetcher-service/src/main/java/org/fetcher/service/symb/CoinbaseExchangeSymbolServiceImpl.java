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
public class CoinbaseExchangeSymbolServiceImpl implements SymbolService {

    @Value("${coinbaseExchange.get-tickets-url}")
    private String coinbaseExchangeGetTicketurl;

    @Value("${coinbaseExchange.redis-key}")
    private String coinbaseExchangeRedisKey;

    private final WebClient webClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> fetchSymbols() {
        return webClient.get()
                .uri(coinbaseExchangeGetTicketurl)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseSymbols)
                .doOnSuccess(symbols -> {
                    redisTemplate.opsForValue().set(
                            coinbaseExchangeRedisKey,
                            symbols,
                            5,
                            TimeUnit.HOURS
                    );
                    log.info("Fetched {} symbols from Coinbase Exchange", symbols.size());
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch symbols from Coinbase Exchange", e);
                    return Mono.error(new RuntimeException("Failed to fetch symbols from Coinbase Exchange", e));
                })
                .block();
    }

    private List<String> parseSymbols(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<String> symbolList = new ArrayList<>();

            for (JsonNode node : root) {
                if (isActiveProduct(node)) {
                    String symbol = node.path("id").asText();
                    String normalizedSymbol = symbol.replace("-", "");
                    symbolList.add(normalizedSymbol);
                }
            }
            return symbolList;
        } catch (Exception e) {
            log.error("Failed to parse symbols from Coinbase response", e);
            return List.of();
        }
    }

    private boolean isActiveProduct(JsonNode productNode) {
        return "online".equals(productNode.path("status").asText()) &&
                !productNode.path("trading_disabled").asBoolean() &&
                !productNode.path("cancel_only").asBoolean() &&
                !productNode.path("post_only").asBoolean() &&
                !productNode.path("limit_only").asBoolean();
    }

    @Override
    public List<String> getAvailableSymbols() {
        List<String> symbols = (List<String>) redisTemplate.opsForValue().get(coinbaseExchangeRedisKey);
        return symbols != null ? symbols : fetchSymbols();
    }
}
