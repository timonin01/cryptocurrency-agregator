package org.fetcher.client;

import org.fetcher.domain.TickerData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExchangeClient {

    Mono<TickerData> getTicker(String symbol);

    Flux<TickerData> getAllTickers();

    String getExchangeName();

    boolean isEnabled();

}
