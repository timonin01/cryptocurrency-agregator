package org.fetcher.client.bybit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.WebSocketExchangeClient;
import org.fetcher.domain.TickerData;
import org.fetcher.service.symb.BybitSymbolServiceImpl;
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
public class BybitWebSocketClient implements WebSocketExchangeClient {

    private final ObjectMapper objectMapper;
    private final BybitSymbolServiceImpl bybitSymbolService;
    private final String websocketUrl;
    private WebSocketClient webSocketClient;
    private Consumer<TickerData> tickerDataConsumer;

    private final boolean bybitWebSocketClientEnabled;
    private List<String> cryptocurrency;

    public BybitWebSocketClient(@Value("${bybit.websocket-url:wss://stream.bybit.com/v5/public/spot}") String websocketUrl,
                                BybitSymbolServiceImpl bybitSymbolService,
                                @Value("${bybit.websocket-enabled:false}") boolean bybitWebSocketClientEnabled) {
        this.websocketUrl = websocketUrl;
        this.bybitSymbolService = bybitSymbolService;
        this.objectMapper = new ObjectMapper();
        this.bybitWebSocketClientEnabled = bybitWebSocketClientEnabled;
    }

    @PostConstruct
    public void init(){
        this.cryptocurrency = bybitSymbolService.getAvailableSymbols();
    }

    @Override
    public void connect(Consumer<TickerData> tickerDataConsumer) {
        this.tickerDataConsumer = tickerDataConsumer;
        try {
            String subscriptionMessage = createSubscriptionMessage();

            webSocketClient = new WebSocketClient(new URI(websocketUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("WebSocket connection opened to Bybit");
                    send(subscriptionMessage);
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(message);
                        if (jsonNode.has("data") && jsonNode.get("data").has("data")) {
                            JsonNode data = jsonNode.get("data").get("data");
                            if (data.has("symbol") && data.has("lastPricePx")) {
                                TickerData tickerData = parseTickerData(data);
                                if (tickerData != null && tickerDataConsumer != null) {
                                    tickerDataConsumer.accept(tickerData);
                                }
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
        try {
            String[] topics = new String[cryptocurrency.size()];
            for (int i = 0; i < cryptocurrency.size(); i++) {
                topics[i] = "tickers." + cryptocurrency.get(i);
            }
            return objectMapper.writeValueAsString(new BybitSubscriptionRequest(
                    "subscribe",
                    topics
            ));
        } catch (Exception e) {
            log.error("Error creating subscription message", e);
            return "{}";
        }
    }

    private TickerData parseTickerData(JsonNode data) {
        try {
            return new TickerData(
                    "BYBIT",
                    data.get("symbol").asText(),
                    new BigDecimal(data.get("lastPricePx").asText()),
                    new BigDecimal(data.get("highPrice24h").asText()),
                    new BigDecimal(data.get("lowPrice24h").asText()),
                    new BigDecimal(data.get("volume24h").asText()),
                    new BigDecimal(data.get("price24hPcnt").asText()).multiply(new BigDecimal("100")),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0L,
                    Instant.now()
            );
        } catch (Exception e) {
            log.error("Error parsing ticker data from JSON: {}", data, e);
            return null;
        }
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("Attempting to reconnect WebSocket to Bybit...");
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
        return "BYBIT";
    }

    @Override
    public boolean isEnabled() {
        return bybitWebSocketClientEnabled;
    }
}