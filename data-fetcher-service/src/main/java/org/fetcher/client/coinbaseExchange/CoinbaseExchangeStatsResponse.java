package org.fetcher.client.coinbaseExchange;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoinbaseExchangeStatsResponse(
        String open,
        String high,
        String low,
        String last,
        String volume,
        @JsonProperty("volume_30day") String volume30day
) {
}
