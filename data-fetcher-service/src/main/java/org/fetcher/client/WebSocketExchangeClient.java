package org.fetcher.client;

import org.fetcher.domain.TickerData;

import java.util.function.Consumer;

public interface WebSocketExchangeClient {

    void connect(Consumer<TickerData> tickerDataConsumer);
    void disconnect();
    boolean isConnected();
    String getExchangeName();
    boolean isEnabled();

}
