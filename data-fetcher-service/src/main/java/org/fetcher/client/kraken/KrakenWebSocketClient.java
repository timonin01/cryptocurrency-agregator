package org.fetcher.client.kraken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.WebSocketExchangeClient;
import org.fetcher.domain.TickerData;
import org.fetcher.service.symb.KrakenSymbolServiceImpl;
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
public class KrakenWebSocketClient implements WebSocketExchangeClient {

    private final ObjectMapper objectMapper;
    private final KrakenSymbolServiceImpl krakenSymbolService;
    private final String websocketUrl;
    private WebSocketClient webSocketClient;
    private Consumer<TickerData> tickerDataConsumer;

    private final boolean krakenWebSocketClientEnabled;
    private List<String> cryptocurrency;

    public KrakenWebSocketClient(@Value("${kraken.websocket-url:wss://ws.kraken.com}") String websocketUrl,
                                 KrakenSymbolServiceImpl krakenSymbolService,
                                 @Value("${kraken.websocket-enabled:false}") boolean krakenWebSocketClientEnabled) {
        this.websocketUrl = websocketUrl;
        this.krakenSymbolService = krakenSymbolService;
        this.objectMapper = new ObjectMapper();
        this.krakenWebSocketClientEnabled = krakenWebSocketClientEnabled;
    }

    @PostConstruct
    public void init(){
        this.cryptocurrency = krakenSymbolService.getAvailableSymbols();
    }

    @Override
    public void connect(Consumer<TickerData> tickerDataConsumer) {
        this.tickerDataConsumer = tickerDataConsumer;
        try {
            String subscriptionMessage = createSubscriptionMessage();
            webSocketClient = new WebSocketClient(new URI(websocketUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("WebSocket connection opened to Kraken");
                    send(subscriptionMessage);
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(message);
                        if (jsonNode.isArray() && jsonNode.size() >= 2) {
                            JsonNode data = jsonNode.get(1);
                            if (data.has("c") && data.has("h") && data.has("l")) {
                                TickerData tickerData = parseTickerData(data, jsonNode.get(0).asInt());
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
            String[] pairs = new String[cryptocurrency.size()];
            for (int i = 0; i < cryptocurrency.size(); i++) {
                String symbol = cryptocurrency.get(i).replace("BTC", "XBT");
                pairs[i] = symbol;
            }
            return objectMapper.writeValueAsString(new KrakenSubscriptionRequest(
                    "subscribe",
                    pairs,
                    "ticker"
            ));
        } catch (Exception e) {
            log.error("Error creating subscription message", e);
            return "{}";
        }
    }

    private TickerData parseTickerData(JsonNode data, int channelId) {
        try {
            String symbol = getСryptocurrencyByChannelId(channelId);

            return new TickerData(
                    "KRAKEN",
                    symbol,
                    new BigDecimal(data.get("c").get(0).asText()),
                    new BigDecimal(data.get("h").get(1).asText()),
                    new BigDecimal(data.get("l").get(1).asText()),
                    new BigDecimal(data.get("v").get(1).asText()),
                    calculatePriceChangePercent(data),
                    data.has("o") ? new BigDecimal(data.get("o").asText()) : BigDecimal.ZERO,
                    data.has("p") && data.get("p").isArray() && data.get("p").size() > 1 
                        ? new BigDecimal(data.get("p").get(1).asText()) : BigDecimal.ZERO,
                    data.has("t") && data.get("t").isArray() && data.get("t").size() > 1 
                        ? data.get("t").get(1).asLong() : 0L,
                    Instant.now()
            );
        } catch (Exception e) {
            log.error("Error parsing ticker data from JSON: {}", data, e);
            return null;
        }
    }

    private String getСryptocurrencyByChannelId(int channelId) {
        if (cryptocurrency.size() > 0) {
            return cryptocurrency.get(0).replace("BTC", "XBT");
        }
        return "XBT/USD";
    }

    private BigDecimal calculatePriceChangePercent(JsonNode data) {
        try {
            BigDecimal currentPrice = new BigDecimal(data.get("c").get(0).asText());
            BigDecimal openPrice = new BigDecimal(data.get("o").asText());

            return currentPrice.subtract(openPrice)
                    .divide(openPrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("Attempting to reconnect WebSocket to Kraken...");
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
        return "KRAKEN";
    }

    @Override
    public boolean isEnabled() {
        return krakenWebSocketClientEnabled;
    }

}