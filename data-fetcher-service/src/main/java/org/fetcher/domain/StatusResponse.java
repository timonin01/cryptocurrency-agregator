package org.fetcher.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
public class StatusResponse {

    private final List<String> availableExchanges;
    private final boolean binanceConnected;
    private final boolean krakenConnected;
    private final boolean bybitConnected;

}
