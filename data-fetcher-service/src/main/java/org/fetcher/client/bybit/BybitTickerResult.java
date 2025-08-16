package org.fetcher.client.bybit;

import java.util.List;

public record BybitTickerResult(
        List<BybitTickerData> list
) {
}
