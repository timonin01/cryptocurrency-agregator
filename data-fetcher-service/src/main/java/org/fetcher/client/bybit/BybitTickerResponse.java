package org.fetcher.client.bybit;

public record BybitTickerResponse(
        String cryptocurrency,
        String lastPrice,
        String highPrice,
        String lowPrice,
        String volume,
        String priceChangePercent
) {
}
