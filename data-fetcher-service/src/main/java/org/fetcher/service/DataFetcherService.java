package org.fetcher.service;

import org.fetcher.client.binance.BinanceRestClient;
import org.fetcher.client.binance.BinanceWebSocketClient;
import org.fetcher.domain.TickerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DataFetcherService {
    private static final Logger logger = LoggerFactory.getLogger(DataFetcherService.class);
    
    private final BinanceRestClient restClient;
    private final BinanceWebSocketClient webSocketClient;
    private final String[] symbols;
    private final AtomicBoolean webSocketEnabled;
    
    private final ConcurrentHashMap<String, TickerData> tickerCache = new ConcurrentHashMap<>();

    public DataFetcherService(BinanceRestClient restClient,
                             BinanceWebSocketClient webSocketClient,
                             @Value("${binance.symbols}") String symbols,
                             @Value("${binance.websocket-enabled:false}") boolean webSocketEnabled) {
        this.restClient = restClient;
        this.webSocketClient = webSocketClient;
        this.symbols = symbols.split(",");
        this.webSocketEnabled = new AtomicBoolean(webSocketEnabled);
    }

    @PostConstruct
    public void init() {
        if (webSocketEnabled.get()) {
            startWebSocketStreaming();
        }
        
        logger.info("DataFetcherService initialized with symbols: {}", Arrays.toString(symbols));
    }

    @PreDestroy
    public void cleanup() {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
        logger.info("DataFetcherService cleanup completed");
    }

    private void startWebSocketStreaming() {
        webSocketClient.connect(this::processTickerData);
        logger.info("WebSocket streaming started");
    }

    private void processTickerData(TickerData tickerData) {
        if (tickerData != null) {
            tickerCache.put(tickerData.symbol(), tickerData);
            
            logger.debug("Received ticker data for {}: price={}, change={}%", 
                tickerData.symbol(), tickerData.lastPrice(), tickerData.priceChangePercent());
        }
    }

    @Scheduled(fixedDelayString = "${binance.rest-poll-interval-sec:10}s")
    public void pollRestData() {
        if (!webSocketEnabled.get() || !webSocketClient.isConnected()) {
            logger.debug("Polling REST data for symbols: {}", Arrays.toString(symbols));
            
            Flux.fromArray(symbols)
                .flatMap(restClient::getTicker)
                .filter(tickerData -> tickerData != null)
                .subscribe(this::processTickerData);
        }
    }

    public TickerData getTickerData(String symbol) {
        return tickerCache.get(symbol);
    }

    public Flux<TickerData> getAllTickerData() {
        return Flux.fromArray(symbols)
                .mapNotNull(tickerCache::get);
    }

    public boolean isWebSocketConnected() {
        return webSocketClient != null && webSocketClient.isConnected();
    }

    public void enableWebSocket() {
        if (webSocketEnabled.compareAndSet(false, true)) {
            startWebSocketStreaming();
            logger.info("WebSocket streaming enabled");
        }
    }

    public void disableWebSocket() {
        if (webSocketEnabled.compareAndSet(true, false)) {
            webSocketClient.disconnect();
            logger.info("WebSocket streaming disabled");
        }
    }
} 