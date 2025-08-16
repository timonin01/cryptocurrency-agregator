package org.fetcher.client.kraken;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class KrakenTickerData {

    @JsonProperty("c")
    private List<String> lastPrice;

    @JsonProperty("h")
    private List<String> highPrice;

    @JsonProperty("l")
    private List<String> lowPrice;

    @JsonProperty("v")
    private List<String> volume;

    @JsonProperty("p")
    private List<String> priceChangePercent;

}
