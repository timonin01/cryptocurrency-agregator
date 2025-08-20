package org.fetcher.client.coinbaseExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.client.ExchangeClient;
import org.fetcher.client.WebSocketExchangeClient;
import org.fetcher.domain.TickerData;
import org.fetcher.service.symb.CoinbaseExchangeSymbolServiceImpl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CoinbaseExchangeWebSocketClient implements WebSocketExchangeClient {

    private final ObjectMapper objectMapper;
    private final CoinbaseExchangeSymbolServiceImpl coinbaseExchangeSymbolService;
    private WebSocketClient webSocketClient;
    private Consumer<TickerData> tickerDataConsumer;

    @Value("${coinbaseExchange.websocket-enabled}")
    private boolean coinbaseExchangeWebSocketClientEnabled;

    @Value("${coinbaseExchange.websocket-url}")
    private String coinbaseExchangeWebSocketUrl;

    private List<String> cryptocurrency;


    @PostConstruct
    public void init(){
        this.cryptocurrency = coinbaseExchangeSymbolService.getAvailableSymbols();
    }

    @Override
    public void connect(Consumer<TickerData> tickerDataConsumer) {
        this.tickerDataConsumer = tickerDataConsumer;
        try {
            String subscriptionMessage = createSubscriptionMessage();

            webSocketClient = new WebSocketClient(new URI(coinbaseExchangeWebSocketUrl)) {

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("WebSocket connection opened to Coinbase Exchange");
                    send(subscriptionMessage);
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(message);
                        if ("ticker".equals(jsonNode.path("type").asText())) {
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
            log.error("Error creating WebSocket connection to Coinbase Exchange", e);
        }
    }

    private String createSubscriptionMessage() {
        try {
            List<String> coinbaseFormattedSymbols = cryptocurrency.stream()
                    .map(symbol -> {
                        if (symbol.contains("-")) {
                            return symbol;
                        }
                        if (symbol.length() > 3) {
                            return symbol.substring(0, 3) + "-" + symbol.substring(3);
                        }
                        return symbol;
                    })
                    .toList();

            CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(
                    "subscribe",
                    coinbaseFormattedSymbols,
                    new String[]{"ticker", "heartbeat"}
            );

            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Error creating subscription message for Coinbase", e);
            return "{}";
        }
    }

    private TickerData parseTickerData(JsonNode data) {
        try {
            String productId = data.path("product_id").asText();
            String formattedSymbol = productId.replace("-", "");

            return new TickerData(
                    "COINBASE_EXCHANGE",
                    formattedSymbol,
                    new BigDecimal(data.path("price").asText("0")),
                    BigDecimal.ZERO, // highPrice - нет в Coinbase ticker
                    BigDecimal.ZERO, // lowPrice - нет в Coinbase ticker
                    new BigDecimal(data.path("volume_24h").asText("0")),
                    BigDecimal.ZERO, // priceChangePercent - нет в Coinbase ticker
                    BigDecimal.ZERO, // openPrice - нет в Coinbase ticker
                    calculateWeightedAvg(data), // средняя цена из bid/ask
                    0L, // count - нет в Coinbase ticker
                    Instant.parse(data.path("time").asText())
            );
        } catch (Exception e) {
            log.error("Error parsing ticker data from Coinbase: {}", data, e);
            return null;
        }
    }

    private BigDecimal calculateWeightedAvg(JsonNode data) {
        try {
            BigDecimal bid = new BigDecimal(data.path("best_bid").asText("0"));
            BigDecimal ask = new BigDecimal(data.path("best_ask").asText("0"));
            if (bid.compareTo(BigDecimal.ZERO) > 0 && ask.compareTo(BigDecimal.ZERO) > 0) {
                return bid.add(ask).divide(new BigDecimal("2"));
            }
            return new BigDecimal(data.path("price").asText("0"));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("Attempting to reconnect WebSocket to Coinbase Exchange...");
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
        return "COINBASE_EXCHANGE";
    }

    @Override
    public boolean isEnabled() {
        return coinbaseExchangeWebSocketClientEnabled;
    }
}
