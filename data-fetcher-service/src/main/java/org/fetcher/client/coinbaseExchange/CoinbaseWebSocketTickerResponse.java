package org.fetcher.client.coinbaseExchange;

import java.math.BigDecimal;
import java.time.Instant;

public record CoinbaseWebSocketTickerResponse(
        BigDecimal price,
        BigDecimal volume24h,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal open24h,
        Instant timestamp,
        BigDecimal priceChangePercent,
        BigDecimal weightedAvg
) {
}
