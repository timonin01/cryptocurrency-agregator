package org.fetcher.domain;

public record BinanceTickerResponse(
        String symbol,
        String lastPrice,
        String highPrice,
        String lowPrice,
        String volume,
        String priceChangePercent
) {}
