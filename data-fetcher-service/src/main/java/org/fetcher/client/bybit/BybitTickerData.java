package org.fetcher.client.bybit;

public record BybitTickerData (
        String symbol,
        String lastPrice,
        String highPrice24h,
        String lowPrice24h,
        String volume24h,
        String turnover24h,
        String price24hPcnt,
        String prevPrice1h,
        String price1hPcnt
){
}
