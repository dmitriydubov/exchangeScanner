package com.exchange.scanner.model;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArbitrageOpportunity {

    private String coinName;

    private String exchangeForBuy;

    private String exchangeForSell;

    private Set<Ask> exchangeForBuyAsks;

    private Set<Bid> exchangeForSellBids;

    private String averagePriceForBuy;

    private String averagePriceForSell;

    private String exchangeForBuyVolume24h;

    private String exchangeForSellVolume24h;

    private TradingData tradingData;
}
