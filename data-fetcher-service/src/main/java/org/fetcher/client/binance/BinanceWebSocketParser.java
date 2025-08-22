package org.fetcher.client.binance;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.fetcher.domain.TickerData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Slf4j
public class BinanceWebSocketParser {
    public TickerData parseTickerData(JsonNode jsonNode) {
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
                    jsonNode.has("o") ? new BigDecimal(jsonNode.get("o").asText()) : BigDecimal.ZERO,
                    jsonNode.has("w") ? new BigDecimal(jsonNode.get("w").asText()) : BigDecimal.ZERO,
                    jsonNode.has("n") ? jsonNode.get("n").asLong() : 0L,
                    eventInstant
            );
        } catch (Exception e) {
            log.error("Error parsing ticker data from JSON: {}", jsonNode, e);
            e.printStackTrace();
            return null;
        }
    }
}
