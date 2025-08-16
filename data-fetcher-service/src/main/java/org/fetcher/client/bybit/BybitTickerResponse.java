package org.fetcher.client.bybit;

import lombok.NonNull;

import java.util.List;

public record BybitTickerResponse(
        int retCode,
        String retMsg,
        BybitTickerResult result
) {
}
