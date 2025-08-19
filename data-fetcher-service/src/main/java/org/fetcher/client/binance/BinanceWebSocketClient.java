package org.fetcher.client.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.WebSocketExchangeClient;
import org.fetcher.domain.TickerData;
import org.fetcher.service.symb.BinanceSymbolServiceImpl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BinanceWebSocketClient implements WebSocketExchangeClient {

    private final ObjectMapper objectMapper;
    private final String websocketUrl;
    private final BinanceSymbolServiceImpl binanceSymbolService;
    private WebSocketClient webSocketClient;
    private Consumer<TickerData> tickerDataConsumer;

    private final boolean binanceWebSocketClientEnabled;
    private List<String> cryptocurrency;

    public BinanceWebSocketClient(@Value("${binance.websocket-url}") String websocketUrl,
                                  BinanceSymbolServiceImpl binanceSymbolService,
                                  @Value("${binance.websocket-enabled}") boolean binanceWebSocketClientEnabled) {
        this.websocketUrl = websocketUrl;
        this.binanceSymbolService = binanceSymbolService;
        this.objectMapper = new ObjectMapper();
        this.binanceWebSocketClientEnabled = binanceWebSocketClientEnabled;
    }

    @PostConstruct
    public void init(){
        try {
            this.cryptocurrency = binanceSymbolService.getAvailableSymbols();
            
            if (this.cryptocurrency == null || this.cryptocurrency.isEmpty()) {
                log.warn("No symbols available from BinanceSymbolService, WebSocket will not start");
                this.cryptocurrency = new ArrayList<>();
            } else {
                log.info("Sample symbols: {}", this.cryptocurrency.subList(0, Math.min(5, this.cryptocurrency.size())));
            }
        } catch (Exception e) {
            log.error("Error loading Binance symbols", e);
            this.cryptocurrency = new ArrayList<>();
        }
    }

    @Override
    public void connect(Consumer<TickerData> tickerDataConsumer) {
        this.tickerDataConsumer = tickerDataConsumer;

        if (this.cryptocurrency == null || this.cryptocurrency.isEmpty()) {
            log.error("Cannot connect to Binance WebSocket: no symbols available");
            return;
        }
        
        log.info("Starting Binance WebSocket connection with {} symbols", this.cryptocurrency.size());
        log.info("First 10 symbols: {}", this.cryptocurrency.subList(0, Math.min(10, this.cryptocurrency.size())));

        try {
            webSocketClient = new WebSocketClient(new URI(websocketUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("WebSocket connection opened to Binance");
                    subscribeToTickers();
                }

                @Override
                public void onMessage(String message) {
                    log.info("WebSocket received: {}", message);
                    try {
                        JsonNode jsonNode = objectMapper.readTree(message);

                        if (jsonNode.has("result") && jsonNode.has("id")) {
                            log.info("Received subscription confirmation: {}", message);
                            return;
                        }
                        
                        JsonNode dataNode = jsonNode.has("data") ? jsonNode.get("data") : jsonNode;
                        
                        if (dataNode.has("s") && dataNode.has("c")) {
                            String symbol = dataNode.get("s").asText();
                            String price = dataNode.get("c").asText();
                            log.info("Processing ticker data: symbol={}, price={}", symbol, price);
                            
                            TickerData tickerData = parseTickerData(dataNode);
                            if (tickerData != null && tickerDataConsumer != null) {
                                log.info("Sending ticker data to consumer: {}", tickerData.cryptocurrency());
                                tickerDataConsumer.accept(tickerData);
                            } else {
                                log.warn("TickerData is null or consumer is null");
                            }
                        } else {
                            log.info("Message doesn't contain ticker data (no 's' or 'c' fields). Message structure: {}", jsonNode.toString());
                        }
                    } catch (Exception e) {
                        log.error("Error parsing WebSocket message: {}", message, e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("WebSocket connection closed: code={}, reason={}, remote={}", code, reason, remote);
                    if (remote) scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket error: {}", ex.getMessage(), ex);
                }
            };
            webSocketClient.connectBlocking();
        } catch (Exception e) {
            log.error("Error creating WebSocket connection", e);
        }
    }

    private void subscribeToTickers() {
        try {
            List<String> symbolsToSubscribe = cryptocurrency.stream()
                    .limit(50)
                    .collect(Collectors.toList());
            
            ArrayNode params = objectMapper.createArrayNode();
            for (String symbol : symbolsToSubscribe) {
                params.add(symbol.toLowerCase() + "@ticker");
            }

            ObjectNode request = objectMapper.createObjectNode();
            request.put("method", "SUBSCRIBE");
            request.set("params", params);
            request.put("id", 1);

            String requestJson = request.toString();
            log.info("Sending subscription request for {} symbols: {}", symbolsToSubscribe.size(), requestJson);

            if (webSocketClient != null && webSocketClient.isOpen()) {
                webSocketClient.send(requestJson);
                log.info("Subscribed to {} ticker streams", params.size());
            } else {
                log.error("WebSocket is not open, cannot subscribe");
            }
        } catch (Exception e) {
            log.error("Failed to subscribe to tickers", e);
        }
    }

    private TickerData parseTickerData(JsonNode jsonNode) {
        try {
            long eventTime = jsonNode.get("E").asLong();
            Instant eventInstant = Instant.ofEpochMilli(eventTime);
            return new TickerData(
                    "BINANCE",
                    jsonNode.get("s").asText(),
                    new BigDecimal(jsonNode.get("c").asText()),
                    new BigDecimal(jsonNode.get("h").asText()),
                    new BigDecimal(jsonNode.get("l").asText()),
                    new BigDecimal(jsonNode.get("v").asText()),
                    new BigDecimal(jsonNode.get("P").asText()),
                    eventInstant
            );
        } catch (Exception e) {
            log.error("Error parsing ticker data from JSON: {}", jsonNode, e);
            e.printStackTrace();
            return null;
        }
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("Attempting to reconnect WebSocket...");
                if (tickerDataConsumer != null) {
                    connect(tickerDataConsumer);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Reconnect thread interrupted", e);
            }
        }).start();
    }

    @Override
    public void disconnect() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
        }
    }

    @Override
    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isOpen();
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public boolean isEnabled() {
        return binanceWebSocketClientEnabled;
    }
}