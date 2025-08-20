package org.fetcher.client.coinbaseExchange;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CoinbaseSubscriptionRequest (
        String type,
        @JsonProperty("product_ids") List<String> productIds,
        String[] channels
){
}
