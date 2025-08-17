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
import java.util.List;
import java.util.function.Consumer;

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
        this.cryptocurrency = binanceSymbolService.getAvailableSymbols();
    }

    @Override
    public void connect(Consumer<TickerData> tickerDataConsumer) {
        this.tickerDataConsumer = tickerDataConsumer;

        try {
            webSocketClient = new WebSocketClient(new URI(websocketUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("WebSocket connection opened to Binance");
                    subscribeToTickers();
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
                    if (remote) scheduleReconnect();
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

    private void subscribeToTickers() {
        try {
            ArrayNode params = objectMapper.createArrayNode();
            for (String symbol : cryptocurrency) {
                params.add(symbol.toLowerCase() + "@ticker");
            }

            ObjectNode request = objectMapper.createObjectNode();
            request.put("method", "SUBSCRIBE");
            request.set("params", params);
            request.put("id", 1);

            if (webSocketClient != null && webSocketClient.isOpen()) {
                webSocketClient.send(request.toString());
                log.info("Subscribed to {} ticker streams", params.size());
            }
        } catch (Exception e) {
            log.error("Failed to subscribe to tickers", e);
        }
    }

    private TickerData parseTickerData(JsonNode jsonNode) {
        try {
            return new TickerData(
                    "BINANCE",
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