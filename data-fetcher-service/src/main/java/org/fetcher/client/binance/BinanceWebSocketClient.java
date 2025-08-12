package org.fetcher.client.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.domain.TickerData;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.function.Consumer;

@Service
@Slf4j
public class BinanceWebSocketClient {

    private final ObjectMapper objectMapper;
    private final String[] symbols;
    private final String websocketUrl;
    private WebSocketClient webSocketClient;
    private Consumer<TickerData> tickerDataConsumer;

    public BinanceWebSocketClient(@Value("${binance.websocket-url}") String websocketUrl,
                                  @Value("${binance.symbols}") String symbols) {
        this.websocketUrl = websocketUrl;
        this.symbols = symbols.split(",");
        this.objectMapper = new ObjectMapper();
    }

    public void connect(Consumer<TickerData> tickerDataConsumer) {
        this.tickerDataConsumer = tickerDataConsumer;
        try {
            String message = createSubscriptionMessage();
            String fullUrl = websocketUrl + "/" + message;
            
            webSocketClient = new WebSocketClient(new URI(fullUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("WebSocket connection opened to Binance");
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(message);
                        if (jsonNode.has("s") && jsonNode.has("c")) {
                            TickerData tickerData = parseTickerData(jsonNode);
                            if (tickerData != null && tickerDataConsumer != null) {
                                tickerDataConsumer.accept(tickerData);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error parsing WebSocket message: {}", message, e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("WebSocket connection closed: code={}, reason={}, remote={}", code, reason, remote);
                    if (remote) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket error", ex);
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            log.error("Error creating WebSocket connection", e);
        }
    }

    private String createSubscriptionMessage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < symbols.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(symbols[i].toLowerCase()).append("@ticker");
        }
        return sb.toString();
    }

    private TickerData parseTickerData(JsonNode jsonNode) {
        try {
            return new TickerData(
                    jsonNode.get("s").asText(),
                    new BigDecimal(jsonNode.get("c").asText()),
                    new BigDecimal(jsonNode.get("h").asText()),
                    new BigDecimal(jsonNode.get("l").asText()),
                    new BigDecimal(jsonNode.get("v").asText()),
                    new BigDecimal(jsonNode.get("P").asText()),
                    Instant.now()
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

    public void disconnect() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
        }
    }

    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isOpen();
    }
} 