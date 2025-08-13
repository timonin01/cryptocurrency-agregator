package org.fetcher.client.binance;

public record BinanceTickerResponse(
        String cryptocurrency,
        String lastPrice,
        String highPrice,
        String lowPrice,
        String volume,
        String priceChangePercent
) {}
