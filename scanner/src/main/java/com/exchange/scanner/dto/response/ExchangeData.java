package com.exchange.scanner.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ExchangeData {

    private Set<String> exchanges;

    private Set<String> coins;

    private Set<String> userMarketsBuy;

    private Set<String> userMarketsSell;

    private Set<String> userCoinsNames;

    private String minUserProfit;

    private String minUserVolume;

    private String maxUserVolume;
}
