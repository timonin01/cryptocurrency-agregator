package org.fetcher.rest;

import lombok.AllArgsConstructor;
import org.fetcher.domain.StatusResponse;
import org.fetcher.domain.TickerData;
import org.fetcher.service.DataFetcherService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
public class DataController {

    private final DataFetcherService dataFetcherService;

    @GetMapping("/tickers")
    public Flux<TickerData> getAllTickerData(@RequestParam(required = false) String cryptocurrency,
            @RequestParam(required = false) String exchangeName) {

        if (cryptocurrency != null && exchangeName != null) {
            return dataFetcherService.getTickerDataForCryptocurrencyAndExchange(cryptocurrency, exchangeName);
        } else if (cryptocurrency != null) {
            return dataFetcherService.getAllTickerData()
                    .filter(ticker -> ticker.cryptocurrency().replace("/", "").contains(cryptocurrency.toUpperCase()));
        } else if (exchangeName != null) {
            return dataFetcherService.getTickerDataFromExchangeName(exchangeName);
        }
        return dataFetcherService.getAllTickerDataInGeneralPage();
    }

    @GetMapping("/exchanges")
    public Flux<String> getAvailableExchanges() {
        return Flux.fromIterable(dataFetcherService.getAvailableExchanges());
    }

    @GetMapping("/status")
    public StatusResponse getStatus() {
        return new StatusResponse(
            dataFetcherService.getAvailableExchanges(),
            dataFetcherService.isWebSocketConnected("binance".toUpperCase()),
            dataFetcherService.isWebSocketConnected("kraken".toUpperCase()),
            dataFetcherService.isWebSocketConnected("bybit".toUpperCase())
        );
    }

    @PostMapping("/websocket/enable")
    public String enableWebSocket() {
        dataFetcherService.enableWebSocket();
        return "WebSocket enabled";
    }

    @PostMapping("/websocket/disable")
    public String disableWebSocket() {
        dataFetcherService.disableWebSocket();
        return "WebSocket disabled";
    }

} 