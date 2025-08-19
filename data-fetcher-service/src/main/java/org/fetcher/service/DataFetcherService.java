package org.fetcher.service;

import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.ExchangeClient;
import org.fetcher.client.WebSocketExchangeClient;
import org.fetcher.domain.TickerData;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DataFetcherService {

    private final List<ExchangeClient> exchangeClients;
    private final List<WebSocketExchangeClient> webSocketExchangeClients;

    private final ConcurrentHashMap<String, TickerData> tickerCache = new ConcurrentHashMap<>();

    public DataFetcherService(List<ExchangeClient> exchangeClients,
                              List<WebSocketExchangeClient> webSocketExchangeClients) {
        this.exchangeClients = exchangeClients;
        this.webSocketExchangeClients = webSocketExchangeClients;
    }

    @PostConstruct
    public void init() {
        log.info("=== DataFetcherService Initialization ===");
        log.info("Found {} exchange clients", exchangeClients.size());
        log.info("Found {} WebSocket clients", webSocketExchangeClients.size());
        
        for (ExchangeClient client : exchangeClients) {
            log.info("Exchange: {} - Enabled: {}", client.getExchangeName(), client.isEnabled());
        }
        
        for (WebSocketExchangeClient client : webSocketExchangeClients) {
            log.info("WebSocket: {} - Enabled: {}", client.getExchangeName(), client.isEnabled());
        }
        
        startWebSocketStreaming();
    }

    @PreDestroy
    public void cleanup() {
        if (webSocketExchangeClients != null) {
            webSocketExchangeClients.forEach(WebSocketExchangeClient::disconnect);
        }
        log.info("DataFetcherService cleanup completed");
    }

    private void startWebSocketStreaming() {
        webSocketExchangeClients.stream()
                .filter(WebSocketExchangeClient::isEnabled)
                .forEach(client -> {
                    client.connect(this::processTickerData);
                    log.info("WebSocket streaming started for {}", client.getClass().getSimpleName());
                });
    }

    private void processTickerData(TickerData tickerData) {
        if (tickerData != null) {
            String cacheKey = tickerData.exchangeName() + ":" + tickerData.cryptocurrency();
            tickerCache.put(cacheKey, tickerData);
            log.info("Received {} data for {}: price={}, change={}%",
                    tickerData.exchangeName(),
                    tickerData.cryptocurrency(),
                    tickerData.lastPrice(),
                    tickerData.priceChangePercent());
        } else {
            log.warn("processTickerData called with null tickerData");
        }
    }

    @Scheduled(fixedDelayString = "${rest-poll-interval-sec:10}s")
    public void pollRestData() {
        Flux.fromIterable(exchangeClients)
                .filter(ExchangeClient::isEnabled)
                .filter(client -> !isWebSocketConnected(client.getExchangeName()))
                .flatMap(client -> client.getAllTickers()
                        .onErrorResume(e -> {
                            log.error("Error fetching from {}: {}", client.getExchangeName(), e.getMessage());
                            return Flux.empty();
                        })
                )
                .filter(Objects::nonNull)
                .subscribe(this::processTickerData);
    }

    public Flux<TickerData> getTickerDataForCryptocurrencyAndExchange(String cryptocurrency, String exchange) {
        String normalizedSymbol = cryptocurrency.replace("/", "").toUpperCase();
        String normalizedExchange = exchange.toUpperCase();

        return getAllTickerData()
                .filter(ticker ->
                        (ticker.cryptocurrency().replace("/", "").contains(normalizedSymbol) &&
                                ticker.exchangeName().equalsIgnoreCase(normalizedExchange)
                        ))
                .take(30);
    }

    public Flux<TickerData> getTickerDataForCryptocurrency(String cryptocurrency) {
        return Flux.fromIterable(tickerCache.values())
                .filter(tickerData -> tickerData.cryptocurrency().equalsIgnoreCase(cryptocurrency) &&
                        tickerData.volume().compareTo(new BigDecimal(0)) > 0)
                .take(30);
    }

    public Flux<TickerData> getTickerDataFromExchangeName(String exchangeName) {
        return Flux.fromIterable(tickerCache.values())
                .filter(tickerData -> tickerData.exchangeName().equalsIgnoreCase(exchangeName) &&
                        tickerData.volume().compareTo(new BigDecimal(0)) > 0)
                .take(30);
    }

    public Flux<TickerData> getAllTickerData() {
        return Flux.fromIterable(tickerCache.values())
                .filter(tickerData -> tickerData.volume().compareTo(new BigDecimal(0)) > 0);
    }

    public Flux<TickerData> getAllTickerDataInGeneralPage() {
        return Flux.fromIterable(tickerCache.values())
                .filter(tickerData -> tickerData.volume().compareTo(new BigDecimal(0)) > 0)
                .take(30);
    }

    public List<String> getAvailableExchanges() {
        return tickerCache.values().stream()
                .map(TickerData::exchangeName)
                .distinct()
                .toList();
    }

    public boolean isAnyWebSocketConnected() {
        return webSocketExchangeClients != null &&
                webSocketExchangeClients.stream()
                        .anyMatch(WebSocketExchangeClient::isConnected);
    }

    public boolean isWebSocketConnected(String exchangeName) {
        return webSocketExchangeClients.stream()
                .filter(client -> client.getExchangeName().equalsIgnoreCase(exchangeName))
                .anyMatch(WebSocketExchangeClient::isConnected);
    }

    public void enableWebSocket() {
        startWebSocketStreaming();
        log.info("WebSocket streaming enabled for all clients");
    }

    public void disableWebSocket() {
        webSocketExchangeClients.forEach(WebSocketExchangeClient::disconnect);
        log.info("WebSocket streaming disabled for all clients");
    }
}