package org.fetcher.rest;

import org.fetcher.domain.TickerData;
import org.fetcher.service.DataFetcherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import lombok.AllArgsConstructor;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/data")
@AllArgsConstructor
public class DataController {
    
    private final DataFetcherService dataFetcherService;

    @GetMapping("/ticker/{symbol}")
    public ResponseEntity<TickerData> getTickerData(@PathVariable String symbol) {
        TickerData tickerData = dataFetcherService.getTickerData(symbol);
        if (tickerData != null) {
            return ResponseEntity.ok(tickerData);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tickers")
    public Flux<TickerData> getAllTickerData() {
        return dataFetcherService.getAllTickerData();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = Map.of(
            "websocketConnected", dataFetcherService.isWebSocketConnected(),
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(status);
    }

    @PostMapping("/websocket/enable")
    public ResponseEntity<String> enableWebSocket() {
        dataFetcherService.enableWebSocket();
        return ResponseEntity.ok("WebSocket streaming enabled");
    }

    @PostMapping("/websocket/disable")
    public ResponseEntity<String> disableWebSocket() {
        dataFetcherService.disableWebSocket();
        return ResponseEntity.ok("WebSocket streaming disabled");
    }
} 