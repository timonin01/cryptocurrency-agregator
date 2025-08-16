package org.fetcher.client.kraken;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class KrakenTickerResponse {

    private List<String> error;
    private Map<String, KrakenTickerData> result;

}
