package org.fetcher.client.binance;

import lombok.NonNull;

public record BinanceTickerResponse(
        String symbol,
        @NonNull String lastPrice,
        @NonNull String highPrice,
        @NonNull String lowPrice,
        @NonNull String volume,
        @NonNull String priceChangePercent,
        String openPrice,
        String weightedAvgPrice,
        String count
) {}
