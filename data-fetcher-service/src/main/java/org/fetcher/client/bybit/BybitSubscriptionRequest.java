package org.fetcher.client.bybit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BybitSubscriptionRequest {

    private String op;
    private String[] args;

}
