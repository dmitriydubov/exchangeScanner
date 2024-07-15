package com.exchange.scanner.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserUpdateMarketData {

    private Set<String> buyExchanges;

    private Set<String> sellExchanges;

    private Set<String> coins;

    private String minProfit;

    private String minDealAmount;

    private String maxDealAmount;
}
