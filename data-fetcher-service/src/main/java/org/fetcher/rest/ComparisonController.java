package org.fetcher.rest;

import lombok.AllArgsConstructor;
import org.fetcher.domain.TickerData;
import org.fetcher.service.DataFetcherService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/comparison")
@AllArgsConstructor
public class ComparisonController {

    private final DataFetcherService dataFetcherService;

    // Сравнить цены по символу между биржами
    @GetMapping("/{cryptocurrency}")
    public Map<String, Object> comparePrices(@PathVariable String cryptocurrency) {
        List<TickerData> tickers = dataFetcherService.getTickerDataForCryptocurrency(cryptocurrency)
                .collectList()
                .block();

        if (tickers == null || tickers.isEmpty()) {
            return Map.of("error", "No data found for symbol: " + cryptocurrency);
        }

        // Сортируем по цене
        List<TickerData> sortedByPrice = tickers.stream()
                .sorted(Comparator.comparing(TickerData::lastPrice))
                .collect(Collectors.toList());

        // Находим лучшие цены
        TickerData lowestPrice = sortedByPrice.get(0);
        TickerData highestPrice = sortedByPrice.get(sortedByPrice.size() - 1);

        // Разница в цене
        double priceDifference = highestPrice.lastPrice().doubleValue() - lowestPrice.lastPrice().doubleValue();
        double priceDifferencePercent = (priceDifference / lowestPrice.lastPrice().doubleValue()) * 100;

        return Map.of(
                "symbol", cryptocurrency,
                "timestamp", java.time.Instant.now(),
                "lowestPrice", Map.of(
                        "exchange", lowestPrice.exchangeName(),
                        "price", lowestPrice.lastPrice(),
                        "volume", lowestPrice.volume()
                ),
                "highestPrice", Map.of(
                        "exchange", highestPrice.exchangeName(),
                        "price", highestPrice.lastPrice(),
                        "volume", highestPrice.volume()
                ),
                "priceDifference", Map.of(
                        "absolute", priceDifference,
                        "percent", priceDifferencePercent
                ),
                "allPrices", tickers.stream()
                        .map(t -> Map.of(
                                "exchange", t.exchangeName(),
                                "price", t.lastPrice(),
                                "change24h", t.priceChangePercent(),
                                "volume", t.volume()
                        ))
                        .collect(Collectors.toList())
        );
    }

    // Получить арбитражные возможности
    @GetMapping("/arbitrage/{cryptocurrency}")
    public Map<String, Object> getArbitrageOpportunities(@PathVariable String cryptocurrency) {
        List<TickerData> tickers = dataFetcherService.getTickerDataForCryptocurrency(cryptocurrency)
                .collectList()
                .block();

        if (tickers == null || tickers.size() < 2) {
            return Map.of("error", "Need at least 2 exchanges for arbitrage");
        }

        // Сортируем по цене
        List<TickerData> sorted = tickers.stream()
                .sorted(Comparator.comparing(TickerData::lastPrice))
                .collect(Collectors.toList());

        TickerData buyFrom = sorted.get(0); // Покупаем по самой низкой цене
        TickerData sellTo = sorted.get(sorted.size() - 1); // Продаем по самой высокой

        double profit = sellTo.lastPrice().doubleValue() - buyFrom.lastPrice().doubleValue();
        double profitPercent = (profit / buyFrom.lastPrice().doubleValue()) * 100;

        return Map.of(
                "symbol", cryptocurrency,
                "timestamp", java.time.Instant.now(),
                "buyFrom", Map.of(
                        "exchange", buyFrom.exchangeName(),
                        "price", buyFrom.lastPrice()
                ),
                "sellTo", Map.of(
                        "exchange", sellTo.exchangeName(),
                        "price", sellTo.lastPrice()
                ),
                "potentialProfit", Map.of(
                        "absolute", profit,
                        "percent", profitPercent
                ),
                "risk", "High - includes fees, slippage, and execution time"
        );
    }
}