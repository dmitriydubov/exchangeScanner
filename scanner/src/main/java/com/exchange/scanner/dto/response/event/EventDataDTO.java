package com.exchange.scanner.dto.response.event;

public record EventDataDTO(
    String exchangeForBuy,
    String exchangeForSell,
    String depositLink,
    String withdrawLink,
    String buyTradingLink,
    String sellTradingLink,
    String fiatVolume,
    String coinVolume,
    String fiatSpread,
    String averagePriceForBuy,
    String averagePriceForSell,
    String priceRangeForBuy,
    String priceRangeForSell,
    String volume24ExchangeForBuy,
    String volume24ExchangeForSell,
    String ordersCountForBuy,
    String ordersCountForSell,
    String spotFee,
    String chainFee,
    String lifeCycle,
    String chainName,
    String transactionTime,
    String transactionConfirmation,
    Boolean margin,
    Boolean isWarning
) {}