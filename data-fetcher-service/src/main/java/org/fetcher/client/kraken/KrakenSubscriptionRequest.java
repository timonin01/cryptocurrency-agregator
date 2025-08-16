package org.fetcher.client.kraken;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class KrakenSubscriptionRequest {

    private String event;
    private String[] pair;
    private String subscription;
}
