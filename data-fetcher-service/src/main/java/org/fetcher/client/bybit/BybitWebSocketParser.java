package org.fetcher.client.bybit;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.domain.TickerData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Slf4j
public class BybitWebSocketParser {

    public TickerData parseTickerData(JsonNode data) {
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

}
