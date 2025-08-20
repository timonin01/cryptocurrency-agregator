package org.fetcher.client.coinbaseExchange;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record CoinbaseTickerResponse (
        @JsonProperty("trade_id") Long tradeId,
        String price,
        String size,
        String time,
        String bid,
        String ask,
        @JsonProperty("volume") String volume24h
){}
