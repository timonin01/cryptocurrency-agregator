package org.fetcher.client.coinbaseExchange;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.domain.TickerData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Slf4j
public class CoinBaseExchangeParser {

    public TickerData parseTickerData(JsonNode data) {
        try {
            String productId = data.path("product_id").asText();
            String formattedSymbol = productId.replace("-", "");

            CoinbaseWebSocketTickerResponse webSocketTickerResponse = createWebSocketTickerResponse(data);
            return new TickerData(
                    "COINBASE_EXCHANGE",
                    formattedSymbol,
                    webSocketTickerResponse.price(),
                    webSocketTickerResponse.high24h(),
                    webSocketTickerResponse.low24h(),
                    webSocketTickerResponse.volume24h(),
                    webSocketTickerResponse.priceChangePercent(),
                    webSocketTickerResponse.open24h(),
                    webSocketTickerResponse.weightedAvg(),
                    0L,
                    webSocketTickerResponse.timestamp()
            );

        } catch (Exception e) {
            log.error("Error parsing ticker data from Coinbase: {}", data, e);
            return null;
        }
    }

    @SuppressWarnings("java:S1488")
    private CoinbaseWebSocketTickerResponse createWebSocketTickerResponse(JsonNode data){
        BigDecimal price = new BigDecimal(data.path("price").asText("0"));
        BigDecimal bestBid = new BigDecimal(data.path("best_bid").asText("0"));
        BigDecimal bestAsk = new BigDecimal(data.path("best_ask").asText("0"));
        BigDecimal open24h = new BigDecimal(data.path("open_24h").asText("0"));
        return new CoinbaseWebSocketTickerResponse(
                price,
                new BigDecimal(data.path("volume_24h").asText("0")),
                bestBid,
                bestAsk,
                new BigDecimal(data.path("high_24h").asText("0")),
                new BigDecimal(data.path("low_24h").asText("0")),
                open24h,
                Instant.parse(data.path("time").asText()),
                calculatePriceChangePercent(open24h, price),
                calculateWeightedAvg(bestBid, bestAsk, price)
        );
    }

    private BigDecimal calculatePriceChangePercent(BigDecimal openPrice, BigDecimal currentPrice) {
        if (openPrice == null || currentPrice == null ||
                openPrice.compareTo(BigDecimal.ZERO) == 0 ||
                currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal change = currentPrice.subtract(openPrice);
            return change.divide(openPrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
        } catch (Exception e) {
            log.warn("Failed to calculate price change percent: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateWeightedAvg(BigDecimal bid, BigDecimal ask, BigDecimal fallbackPrice) {
        if (bid != null && ask != null &&
                bid.compareTo(BigDecimal.ZERO) > 0 && ask.compareTo(BigDecimal.ZERO) > 0) {
            try {
                return bid.add(ask).divide(new BigDecimal("2"));
            } catch (Exception e) {
                return fallbackPrice;
            }
        }
        return fallbackPrice;
    }
}
