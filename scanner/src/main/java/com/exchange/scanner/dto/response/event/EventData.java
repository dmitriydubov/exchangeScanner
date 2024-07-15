package com.exchange.scanner.dto.response.event;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventData {

    private String exchangeForBuy;

    private String exchangeForSell;

    private String depositLink;

    private String withdrawLink;

    private String buyTradingLink;

    private String sellTradingLink;

    private String fiatVolume;

    private String coinVolume;

    private String fiatSpread;

    private String averagePriceForBuy;

    private String averagePriceForSell;

    private String priceRangeForBuy;

    private String priceRangeForSell;

    private String volume24ExchangeForBuy;

    private String volume24ExchangeForSell;

    private String ordersCountForBuy;

    private String ordersCountForSell;

    private String spotFee;

    private String chainFee;

    private String chainName;

    private String transactionTime;

    private String transactionConfirmation;

    private Boolean margin;

    private List<String> futures;

    private String slug;

    private Boolean isWarning;

    private String lifeCycle;
}
