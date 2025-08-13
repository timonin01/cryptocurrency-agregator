package org.fetcher.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TickerData(
        String exchangeName,
        String cryptocurrency,
        BigDecimal lastPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal volume,
        BigDecimal priceChangePercent,
        Instant timestamp
) {}
