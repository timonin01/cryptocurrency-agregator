package org.fetcher.client.kraken;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.domain.TickerData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Slf4j
public class KrakenWebSocketParser {

    public TickerData parseTickerData(JsonNode data, String symbol) {
        try {
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

}
